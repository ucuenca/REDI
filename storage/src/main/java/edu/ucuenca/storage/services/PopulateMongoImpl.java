/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.ucuenca.storage.services;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import edu.ucuenca.storage.api.MongoService;
import edu.ucuenca.storage.api.PopulateMongo;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.marmotta.platform.core.api.config.ConfigurationService;
import org.apache.marmotta.platform.core.api.task.Task;
import org.apache.marmotta.platform.core.api.task.TaskManagerService;
import org.apache.marmotta.platform.core.api.triplestore.SesameService;
import org.apache.marmotta.platform.core.exception.MarmottaException;
import org.apache.marmotta.platform.sparql.api.sparql.SparqlService;
import org.apache.marmotta.ucuenca.wk.commons.service.QueriesService;
import org.apache.marmotta.ucuenca.wk.commons.service.CommonsServices;
import org.apache.marmotta.ucuenca.wk.commons.service.DistanceService;
import org.apache.marmotta.ucuenca.wk.commons.service.ExternalSPARQLService;
import org.apache.marmotta.ucuenca.wk.commons.util.BoundedExecutor;
import org.apache.marmotta.ucuenca.wk.pubman.api.CommonService;
import org.bson.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.slf4j.Logger;

/**
 *
 * @author Xavier Sumba <xavier.sumba93@ucuenca.ec>
 */
@ApplicationScoped
public class PopulateMongoImpl implements PopulateMongo {

  @Inject
  private ConfigurationService conf;
  @Inject
  private QueriesService queriesService;
  @Inject
  private SparqlService sparqlService;
  @Inject
  private ExternalSPARQLService fastSparqlService;
  @Inject
  private SesameService sesameService;
  @Inject
  private Logger log;
  @Inject
  private CommonService commonService;
  @Inject
  private CommonsServices commonServices;
  @Inject
  private DistanceService distservice;
  @Inject
  private TaskManagerService taskManagerService;

  private static final Map context = new HashMap();

  private final JsonNodeFactory factory = JsonNodeFactory.instance;

  static {
    context.put("dct", "http://purl.org/dc/terms/");
    context.put("owl", "http://www.w3.org/2002/07/owl#");
    context.put("foaf", "http://xmlns.com/foaf/0.1/");
    context.put("uc", "http://ucuenca.edu.ec/ontology#");
    context.put("bibo", "http://purl.org/ontology/bibo/");
    context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
    context.put("schema", "http://schema.org/");
  }

  /**
   *
   * @param queryResources query to load resources to describe.
   * @param queryDescribe query to describe each candidate; it has to be a
   * describe/construct.
   * @param collection collection name in Mongo db.
   */
  private void loadResources(String queryResources, String queryDescribe, String c) {
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));
            StringWriter writter = new StringWriter();) {
      RepositoryConnection conn = sesameService.getConnection();

      int num_candidates = 0;
      try {
        MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
        // Delete and create collection
        MongoCollection<Document> collection = db.getCollection(c);
        collection.drop();

        RDFWriter jsonldWritter = Rio.createWriter(RDFFormat.JSONLD, writter);
        TupleQueryResult resources = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryResources).evaluate();
        while (resources.hasNext()) {
          String resource = resources.next().getValue("subject").stringValue();
          conn.prepareGraphQuery(QueryLanguage.SPARQL, queryDescribe.replace("{}", resource))
                  .evaluate(jsonldWritter);
          Object compact = JsonLdProcessor.compact(JsonUtils.fromString(writter.toString()), context, new JsonLdOptions());
          Map<String, Object> json = (Map<String, Object>) compact;
          json.put("_id", resource);
          collection.insertOne(new Document(json));
          writter.getBuffer().setLength(0);
          log.info("{} inserting describe for resource {}", ++num_candidates, resource);
        }
        log.info("Load {} resources into {} collection", num_candidates, collection);
      } finally {
        conn.close();
      }
    } catch (RepositoryException ex) {
      log.error("Cannot retrieve Sesame connection", ex);
    } catch (MalformedQueryException ex) {
      log.error("Query cannot be processed", ex);
    } catch (QueryEvaluationException ex) {
      log.error("Execution of query failed", ex);
    } catch (RDFHandlerException ex) {
      log.error("Cannot handle RDFWriter", ex);
    } catch (JsonLdError ex) {
      log.error("Cannot convert string to JSON-LD", ex);
    } catch (IOException ex) {
      log.error("IO error", ex);
    }
  }

  private void loadStadistics(String c, HashMap<String, String> queries) {
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));
            StringWriter writter = new StringWriter();) {
      RepositoryConnection conn = sesameService.getConnection();

      try {
        MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
        // Delete and create collection
        MongoCollection<Document> collection = db.getCollection(c);
        collection.drop();

        RDFWriter jsonldWritter = Rio.createWriter(RDFFormat.JSONLD, writter);
        for (String key : queries.keySet()) {
          log.info("Getting {} query", key);

          conn.prepareGraphQuery(QueryLanguage.SPARQL, queries.get(key))
                  .evaluate(jsonldWritter);
          Object compact = JsonLdProcessor.compact(JsonUtils.fromString(writter.toString()), context, new JsonLdOptions());
          Map<String, Object> json = (Map<String, Object>) compact;
          json.put("_id", key);
          collection.insertOne(new Document(json));
          writter.getBuffer().setLength(0);
          log.info("Load aggregation into {} collection for id '{}'", c, key);
        }
      } finally {
        conn.close();
      }
    } catch (RepositoryException ex) {
      log.error("Cannot retrieve Sesame connection", ex);
    } catch (MalformedQueryException ex) {
      log.error("Query cannot be processed", ex);
    } catch (QueryEvaluationException ex) {
      log.error("Execution of query failed", ex);
    } catch (RDFHandlerException ex) {
      log.error("Cannot handle RDFWriter", ex);
    } catch (JsonLdError ex) {
      log.error("Cannot convert string to JSON-LD", ex);
    } catch (IOException ex) {
      log.error("IO error", ex);
    }
  }

  @Override
  public void authors() {
    final Task task = taskManagerService.createSubTask("Caching authors profiles", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
      // Delete and create collection
      final MongoCollection<Document> collection = db.getCollection(MongoService.Collection.AUTHORS.getValue());
      collection.drop();
      final List<Map<String, Value>> authorsRedi = sparqlService.query(QueryLanguage.SPARQL, queriesService.getAuthorsCentralGraph());
      task.updateTotalSteps(authorsRedi.size());
      BoundedExecutor threadPool = BoundedExecutor.getThreadPool(5);
      for (int i = 0; i < authorsRedi.size(); i++) {
        final String author = authorsRedi.get(i).get("a").stringValue();
        final int j = i;
        threadPool.submitTask(new Runnable() {
          @Override
          public void run() {
            // Print progress
            log.info("Relating {} ", author);
            log.info("Relating {}/{}. Author: '{}' ", j + 1, authorsRedi.size(), author);
            task.updateDetailMessage("URI", author);
            task.updateProgress(j + 1);
            // Get and store author data (json) from SPARQL repository.
            String profiledata = commonService.getAuthorDataProfile(author);
            Document parse = Document.parse(profiledata);
            parse.append("_id", author);
            collection.insertOne(parse);
          }
        });
      }
      threadPool.end();
    } catch (MarmottaException | InterruptedException ex) {
      log.error(ex.getMessage(), ex);
    }
    taskManagerService.endTask(task);
  }

  @Override
  public void statistics() {
    HashMap<String, String> queries = new HashMap<>();

    queries.put("barchar", queriesService.getBarcharDataQuery());

    if (countCountries() > 1) {
      queries.put("count_authors", queriesService.getAggregationPublicationsbyCountry());
      queries.put("count_publications", queriesService.getAggreggationAuthorsbyCountry());
    } else {
      queries.put("count_authors", queriesService.getAggreggationAuthors());
      queries.put("count_publications", queriesService.getAggregationPublications());
    }
    queries.put("count_research_areas", queriesService.getAggregationAreas());
    queries.put("keywords_frequencypub_gt4", queriesService.getKeywordsFrequencyPub());
    loadStadistics(MongoService.Collection.STATISTICS.getValue(), queries);
  }

  @Override
  public void LoadStatisticsbyInst() {
    Task task = taskManagerService.createSubTask("Caching statistics by Institution", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.STATISTICS_INST.getValue());
      collection.drop();

      List<String> queries = new ArrayList();
      queries.add("inst_by_area");
      queries.add("pub_by_date");
      queries.add("author_by_inst");
      queries.add("inst_by_inst");
      queries.add("prov_by_inst");

      String uri = "";
      String name = "";
      String fullname = "";
      List<Map<String, Value>> org = sparqlService.query(QueryLanguage.SPARQL, queriesService.getListOrganizationQuery());
      Document parse = new Document();
      task.updateTotalSteps((org.size() + 1) * (queries.size() + 1));
      int ints = 0;
      for (Map<String, Value> o : org) {

        uri = o.get("URI").stringValue();
        name = o.get("name").stringValue();
        fullname = o.get("fullNameEs").stringValue();
        task.updateDetailMessage("Institution ", uri);
        for (String q : queries) {
          ints++;
          String response = statisticsbyInstQuery(uri, q);

          parse.append(q, Document.parse(response));

          log.info("Stats Inst {} ", uri);
          log.info("Query {}", q);

          task.updateProgress(ints);

        }

        parse.append("_id", uri);
        parse.append("name", name);
        parse.append("fullname", fullname);
        collection.insertOne(parse);
      }
      taskManagerService.endTask(task);
      // loadStadistics(MongoService.Collection.STATISTICS.getValue(), queries);
    } catch (MarmottaException ex) {
      log.error("erro" + ex);
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.INFO, null, ex);
    }
  }

  private String statisticsbyInstQuery(String uri, String query) throws MarmottaException {
    // List <Map<String,String>>   
    switch (query) {
      case "inst_by_area":
        return this.getStatsInstbyArea(uri);
      case "pub_by_date":
        return this.getStatsInstbyPubDate(uri);
      case "author_by_inst":
        return this.getTopAuthorbyInst(uri);
      case "inst_by_inst":
        return this.getTopInstbyInst(uri);
      case "prov_by_inst":
        return this.getTopProvbyInst(uri);
      default:
        return null;

    }

  }

  private int countCountries() {
    try {
      List<Map<String, Value>> countc = sparqlService.query(QueryLanguage.SPARQL, queriesService.getCountCountry());
      return Integer.parseInt(countc.get(0).get("ncountry").stringValue());

    } catch (MarmottaException ex) {
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
    }
    return 0;
  }

  @Override
  public void networks() {
    final Task task = taskManagerService.createSubTask("Caching related authors", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
      // Delete and create collection
      final MongoCollection<Document> collection = db.getCollection(MongoService.Collection.RELATEDAUTHORS.getValue());
      collection.drop();
      BoundedExecutor threadPool = BoundedExecutor.getThreadPool(5);
      task.updateMessage("Calculating related authors");
      final List<Map<String, Value>> query = fastSparqlService.getSparqlService().query(QueryLanguage.SPARQL, queriesService.getAuthorsCentralGraph());
      int i = 0;
      for (final Map<String, Value> mp : query) {
        final int j = i++;
        threadPool.submitTask(new Runnable() {
          @Override
          public void run() {
            String stringValue = mp.get("a").stringValue();
            log.info("Relating {} ", stringValue);
            log.info("Relating {}/{} ", j, query.size());
            task.updateDetailMessage("URI", stringValue);
            task.updateDetailMessage("Status", j + "/" + query.size());
            String collaboratorsData = commonService.getCollaboratorsData(stringValue);
            Document parse = Document.parse(collaboratorsData);
            parse.append("_id", stringValue);
            collection.insertOne(parse);
          }
        });

      }
      threadPool.end();
    } catch (Exception w) {
      log.debug(w.getMessage(), w);
    }
    taskManagerService.endTask(task);
  }

  @Override
  public void clusters() {
    Task task = taskManagerService.createSubTask("Caching clusters", "Mongo Service");
    clustersTotals();
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());

      // Delete and create collection
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.CLUSTERS.getValue());
      collection.drop();

      List<Map<String, Value>> clusters = sparqlService.query(QueryLanguage.SPARQL, queriesService.getClusterURIs());

      task.updateTotalSteps(clusters.size());

      for (int i = 0; i < clusters.size(); i++) {
        String cluster = clusters.get(i).get("c").stringValue();
        // Print progress
        log.info("Relating {}/{}. Cluster: '{}' ", i + 1, clusters.size(), cluster);
        task.updateDetailMessage("URI", cluster);
        task.updateProgress(i + 1);
        // Get and store author data (json) from SPARQL repository.
        String clusterData = commonService.getCluster(cluster);
        Document parse = Document.parse(clusterData);
        parse.append("_id", cluster);
        collection.insertOne(parse);
      }
    } catch (MarmottaException ex) {
      log.error(ex.getMessage(), ex);
    } finally {
      taskManagerService.endTask(task);
    }
  }

  public void clustersTotals() {
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());

      // Delete and create collection
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.CLUSTERSTOTALS.getValue());
      collection.drop();
      log.info("Counting clusters");
      List<Map<String, Value>> query = sparqlService.query(QueryLanguage.SPARQL, queriesService.getClusterTotals());
      log.info("Writing totals");
      for (Map<String, Value> a : query) {
        String label = a.get("k").stringValue();
        log.info("Cluster {}", label);
        String uri = a.get("area").stringValue();
        String tot = a.get("totalAuthors").stringValue();
        Document parse = new Document();
        parse.append("_id", uri);
        parse.append("area", uri);
        parse.append("k", label);
        parse.append("totalAuthors", tot);
        List<BasicDBObject> lsdoc = new ArrayList<>();
        List<Map<String, Value>> query1 = sparqlService.query(QueryLanguage.SPARQL, queriesService.getSubClusterTotals(uri));
        for (Map<String, Value> b : query1) {
          if (b.get("sc") == null) {
            continue;
          }
          String sc = b.get("sc").stringValue();
          String k = b.get("k").stringValue();
          String totalAuthors = b.get("totalAuthors").stringValue();
          BasicDBObject parseSub = new BasicDBObject();
          parseSub.put("sc", sc);
          parseSub.put("k", k);
          parseSub.put("totalAuthors", totalAuthors);
          lsdoc.add(parseSub);
        }
        parse.append("subclusters", lsdoc);
        collection.insertOne(parse);
      }

    } catch (MarmottaException ex) {
      log.error(ex.getMessage(), ex);
    }
  }

  @Override
  public void authorsByArea() {
    final Task task = taskManagerService.createSubTask("Caching Authors by Area", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());

      // Delete and create collection
      final MongoCollection<Document> collection = db.getCollection(MongoService.Collection.AUTHORS_AREA.getValue());
      collection.drop();

      final List<Map<String, Value>> areas = sparqlService.query(QueryLanguage.SPARQL, queriesService.getClusterAndSubclusterURIs());

      task.updateTotalSteps(areas.size());
      BoundedExecutor threadPool = BoundedExecutor.getThreadPool(5);
      for (int i = 0; i < areas.size(); i++) {
        final int j = i;
        final String cluster = areas.get(i).get("cluster").stringValue();
        final String subcluster = areas.get(i).get("subcluster").stringValue();

        threadPool.submitTask(new Runnable() {
          @Override
          public void run() {
            // Print progress
            log.info("Relating {}/{}. Cluster: '{}' - Subcluster: '{}'", j + 1, areas.size(), cluster, subcluster);
            task.updateDetailMessage("Cluster", cluster);
            task.updateDetailMessage("Subluster", subcluster);
            task.updateProgress(j + 1);
            // Get authors of an area from the SPARQL endpoint and transform them to JSON .
            String authorsByArea = commonService.getsubClusterGraph(cluster, subcluster);
            Document parse = Document.parse(authorsByArea);
            BasicDBObject key = new BasicDBObject();
            key.put("cluster", cluster);
            key.put("subcluster", subcluster);
            parse.append("_id", key);
            collection.insertOne(parse);
          }
        });

      }
      threadPool.end();
    } catch (MarmottaException | InterruptedException ex) {
      log.error(ex.getMessage(), ex);
    } finally {
      taskManagerService.endTask(task);
    }
  }

  @Override
  public void authorsByDiscipline() {
    final Task task = taskManagerService.createSubTask("Caching Authors by Discipline", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());

      // Delete and create collection
      final MongoCollection<Document> collection = db.getCollection(MongoService.Collection.AUTHORS_DISCPLINE.getValue());
      collection.drop();

      final List<Map<String, Value>> clusters = sparqlService.query(QueryLanguage.SPARQL, queriesService.getClusterURIs());

      task.updateTotalSteps(clusters.size());
      BoundedExecutor threadPool = BoundedExecutor.getThreadPool(5);
      for (int i = 0; i < clusters.size(); i++) {
        final int j = i;
        final String cluster = clusters.get(i).get("c").stringValue();
        threadPool.submitTask(new Runnable() {
          @Override
          public void run() {
            // String subcluster = areas.get(i).get("subcluster").stringValue();
            // Print progress
            log.info("Relating {}/{}. Cluster: '{}'", j + 1, clusters.size(), cluster);
            task.updateDetailMessage("Cluster", cluster);
            // task.updateDetailMessage("Subluster", subcluster);
            task.updateProgress(j + 1);
            // Get authors of an area from the SPARQL endpoint and transform them to JSON .
            String authorsByDisc = commonService.getClusterGraph(cluster);
            Document parse = Document.parse(authorsByDisc);
            BasicDBObject key = new BasicDBObject();
            key.put("cluster", cluster);
            //key.put("subcluster", subcluster);
            parse.append("_id", key);
            collection.insertOne(parse);
          }
        });

      }
      threadPool.end();
    } catch (MarmottaException | InterruptedException ex) {
      log.error(ex.getMessage(), ex);
    } finally {
      taskManagerService.endTask(task);
    }
  }

  @Override
  public void Countries() {
    Task task = taskManagerService.createSubTask("Caching countries", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());

      // Delete and create collection
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.COUNTRIES.getValue());
      collection.drop();
      try {
        List<Map<String, Value>> countries = sparqlService.query(QueryLanguage.SPARQL, queriesService.getCountries());
        task.updateTotalSteps(countries.size());
        for (int i = 0; i < countries.size(); i++) {
          String co = countries.get(i).get("co").stringValue();
          String code = getCountryCode(co);
          String countriesNodes = countrynodes(co, code).toString();
          Document parse = Document.parse(countriesNodes);
          parse.append("_id", co);
          collection.insertOne(parse);

          task.updateDetailMessage("Country", co);
          task.updateProgress(i + 1);
        }
      } catch (MarmottaException ex) {
        java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
      } finally {
        taskManagerService.endTask(task);
      }
    }
  }

  private ObjectNode countrynodes(String name, String code) {
    ObjectNode node = factory.objectNode(); // initializing
    node.put("name", name);
    node.put("code", code);
    return node;
  }

  private String getCountryCode(String coName) {

    try {
      String Country = URLEncoder.encode(coName, "UTF-8").replace("+", "%20");
      Object js = commonServices.getHttpJSON("https://restcountries.eu/rest/v2/name/" + Country);
      JSONArray json = null;
      if (js instanceof JSONArray) {
        json = (JSONArray) js;
      }
      System.out.print(js);
      for (Object j : json) {
        // System.out.println ("ite");
        // System.out.println ((JSONObject) j);
        JSONObject jo = (JSONObject) j;
        Object r = jo.get("alpha2Code");
        // System.out.println ("---"+r);
        return r.toString();

      }
    } catch (UnsupportedEncodingException ex) {
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public void publications() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void cleanSPARQLS() {
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.SPARQLS.getValue());
      collection.drop();
    }
  }

  public String getStatsInstbyPubDate(String uri) throws MarmottaException {
    List<Map<String, Value>> years = sparqlService.query(QueryLanguage.SPARQL, queriesService.getDatesPubbyInst(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> y : years) {
      JSONObject obj = new JSONObject();
      obj.put("y", y.get("y").stringValue());
      obj.put("total", y.get("total").stringValue());
      array.add(obj);
    }
    main.put("data", array);
    return main.toJSONString();

  }

  public String getStatsInstbyArea(String uri) throws MarmottaException {
    List<Map<String, Value>> area = sparqlService.query(QueryLanguage.SPARQL, queriesService.getClustersbyInst(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> a : area) {
      JSONObject obj = new JSONObject();
      obj.put("uri", a.get("area").stringValue());
      obj.put("name", a.get("nameng").stringValue());
      obj.put("total", a.get("total").stringValue());
      array.add(obj);
    }
    main.put("data", array);
    return main.toJSONString();
  }

  public String getTopAuthorbyInst(String uri) throws MarmottaException {
    List<Map<String, Value>> author = sparqlService.query(QueryLanguage.SPARQL, queriesService.getAuthorsbyInst(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> a : author) {
      JSONObject obj = new JSONObject();
      obj.put("uri", a.get("author").stringValue());
      obj.put("name", commonService.getUniqueName(a.get("name").stringValue(), ";"));
      obj.put("total", a.get("total").stringValue());
      array.add(obj);
    }
    main.put("data", array);
    return main.toJSONString();
  }

  public String getTopInstbyInst(String uri) throws MarmottaException {
    List<Map<String, Value>> inst = sparqlService.query(QueryLanguage.SPARQL, queriesService.getInstAsobyInst(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> ins : inst) {
      JSONObject obj = new JSONObject();
      obj.put("uri", ins.get("org").stringValue());
      obj.put("name", ins.get("norg").stringValue());
      obj.put("total", ins.get("total").stringValue());
      array.add(obj);
    }
    main.put("data", array);
    return main.toJSONString();
  }

  public String getTopProvbyInst(String uri) throws MarmottaException {

    List<Map<String, Value>> prov = sparqlService.query(QueryLanguage.SPARQL, queriesService.getProvbyInst(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> p : prov) {
      JSONObject obj = new JSONObject();
      String uriprov = p.get("prov").stringValue();
      obj.put("uri", uriprov);
      obj.put("name", providerName(uriprov.substring(uriprov.lastIndexOf("#") + 1)));
      obj.put("total", p.get("total").stringValue());
      array.add(obj);
    }
    main.put("data", array);

    return main.toJSONString();
  }

  public String providerName(String text) {
    return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(text), ' ').replace("Provider", "");
  }

  public String getStatsAuthorbyPubDate(String uri) throws MarmottaException {
    List<Map<String, Value>> years = sparqlService.query(QueryLanguage.SPARQL, queriesService.getAuthorPubbyDate(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> y : years) {
      JSONObject obj = new JSONObject();
      if (y.containsKey("y")) {
        obj.put("y", y.get("y").stringValue());
        obj.put("total", y.get("total").stringValue());
        array.add(obj);
      }
    }
    main.put("data", array);
    return main.toJSONString();
  }

  public String getStatsRelevantKbyAuthor(String uri) throws MarmottaException {
    List<Map<String, Value>> key = sparqlService.query(QueryLanguage.SPARQL, queriesService.getRelevantKbyAuthor(uri, 15));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> k : key) {
      if (k.containsKey("lsubject")) {
        JSONObject obj = new JSONObject();
        obj.put("subject", k.get("lsubject").stringValue());
        obj.put("total", k.get("npub").stringValue());

        array.add(obj);
      }
    }
    main.put("data", array);
    return main.toJSONString();
  }

  /*  public String getStatsConferencebyAuthor (String uri) throws MarmottaException { 
     List<Map<String, Value>> prov = sparqlService.getSparqlService().query(QueryLanguage.SPARQL, queriesService.getConferencebyAuthor(uri));
     JSONObject main = new JSONObject();
     JSONArray array = new JSONArray();
     for (Map<String, Value> p :prov){
     JSONObject obj = new JSONObject();
     obj.put("name", p.get("name").stringValue());
     obj.put("total",p.get("total").stringValue());
     array.add(obj);
     }
     main.put("data", array);
    
     return main.toJSONString();
     }*/
  public String getStatsConferencebyAuthor(String uri) throws MarmottaException {
    //  List<Map<String, Value>> prov1 = sparqlService.getSparqlService().query(QueryLanguage.SPARQL, queriesService.getConferencebyAuthor(uri));
    // System.out.print (prov1);

    List<Map<String, Value>> prov = sparqlService.query(QueryLanguage.SPARQL, queriesService.getConferencebyAuthor(uri));
    System.out.print("Dentro ");

    Map<String, Integer> auxmap = new HashMap();
    for (Map<String, Value> p : prov) {
      if (!p.containsKey("name")) {
        continue;
      }
      String[] cand = p.get("name").stringValue().split(";");
      for (String c : cand) {
        String[] w = c.split(" ");
        for (String word : w) {
          String cw = word.replaceAll("\\)|\\(|:|,", " ").trim();
          if (cw.length() > 2 && !StringUtils.isNumericSpace(cw) && cw.equals(cw.toUpperCase())) {
            if (!auxmap.containsKey(cw)) {
              auxmap.put(cw, 0);
            } else {
              auxmap.put(cw, auxmap.get(cw) + 1);
            }
          }
        }
      }

      Iterator it = auxmap.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pair = (Map.Entry) it.next();
        if (p.get("name").stringValue().contains(pair.getKey().toString())) {
          auxmap.put(pair.getKey().toString(), (Integer) pair.getValue() + Integer.parseInt(p.get("total").stringValue()));
        }
      }

    }
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    List keys = new ArrayList(auxmap.keySet());
    Collections.sort(keys);
    System.out.print(keys);
    for (String key : auxmap.keySet()) {
      JSONObject obj = new JSONObject();
      obj.put("name", key);
      obj.put("total", auxmap.get(key));
      array.add(obj);
    }

    main.put("data", array);

    return main.toJSONString();
  }

  //DATATEST
/*
    public List<Map<String, String>> getdata() {
        String[] name = {"Maskana;MASKANA", "eswc;ESWC", "IEEE Transactions on Industrial Electronics",
            "eswc;ESWC;European Semantic Web Conference", "RISTI: Revista Ibérica de Sistemas e Tecnologias de Informação ", "2017 XLIII Latin American Computer Conference (CLEI) ", "Computer Conference (CLEI), 2017 XLIII Latin American ",
            "Ecuador Technical Chapters Meeting (ETCM), 2017 IEEE",
            "2017 IEEE 2nd Ecuador Technical Chapters Meeting, ETCM 2017 ", "iecon;IECON"};
        String[] total = {"6", "4", "4", "4", "4", "4", "4", "4", "3", "3"};
        List<Map<String, String>> prov = new ArrayList();
        for (int i = 0; i < name.length; i++) {
            Map<String, String> m = new HashMap();
            m.put("name", name[i]);
            m.put("total", total[i]);
            prov.add(m);
        }

        return prov;

    }*/
  // DATATEST 
  /*  public List<Map<String, String>> getdataAff() {
        String[] name = {"Universidad Politécnica de Madrid", "Universidad de Cuenca", "Departamento de Ciencias de la Computación, Universidad de Cuenca, Av. de Abril s/n y Agustín Cueva, Cuenca, Ecuador,",
            "UCUENCA", "University of Zaragoza ", "Universidad Politecnica de Madrid", "Universidad de Cuenca",
            "Universidad Politecnica de Madrid",
            "Universidad de Cuenca, Universidad de Zaragoza, UPM"};
        //String [] total = {"6","4","4","4","4","4","4","4","3","3"};
        List<Map<String, String>> prov = new ArrayList();
        for (int i = 0; i < name.length; i++) {
            Map<String, String> m = new HashMap();
            m.put("orgname", name[i]);
            //m.put("total", total[i]);
            prov.add(m);
        }

        return prov;

    }*/
 /*providerName(uriprov.substring(uriprov.lastIndexOf("#") + 1)) */
  public String getStatsProvbyAuthor(String uri) throws MarmottaException {
    List<Map<String, Value>> key = sparqlService.query(QueryLanguage.SPARQL, queriesService.getRelevantProvbyAuthor(uri));
    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    for (Map<String, Value> k : key) {
      if (k.containsKey("prov")) {
        String uriprov = k.get("prov").stringValue();
        JSONObject obj = new JSONObject();
        obj.put("uri", uriprov);
        obj.put("prov", providerName(uriprov.substring(uriprov.lastIndexOf("#") + 1)));
        obj.put("total", k.get("total").stringValue());

        array.add(obj);
      }
    }
    main.put("data", array);
    return main.toJSONString();
  }

  private String getLang(Value value) {
    if (value instanceof Literal) {
      Literal lit = (Literal) value;
      return lit.getLanguage();
    }
    return "";
  }

  public String getStatsAffbyAuthor(String uri) throws MarmottaException {
    //   List<Map<String, String>> provn = getdataAff();
    List<Map<String, Value>> provn = sparqlService.query(QueryLanguage.SPARQL, queriesService.getOrgbyAuyhor(uri));
    List<Map<String, Integer>> affl = new ArrayList();
    Map<String, Integer> auxmap = new HashMap();
    List<String> blackl = new ArrayList();
    // affl.add(auxmap);

    for (Map<String, Value> p : provn) {
      if (!p.containsKey("orgname")) {
        continue;
      }
      String l = getLang(p.get("orgname"));

      if ("en".equals(l)) {
        blackl.add(p.get("orgname").stringValue().trim().toLowerCase());
        continue;
      }

      String[] cand = p.get("orgname").stringValue().split("(;)|(,)");
      String uricand = p.get("org").stringValue();
      Map<String, Integer> newmap = new HashMap();
      for (String c : cand) {
        String word = c.replaceAll("\\)|\\(|:|,", " ").trim().toLowerCase();
        if (validwords(word)) {
          newmap.put(word, 0);
        }

      }
      if (affl.isEmpty()) {
        affl.add(newmap);
      } else {
        boolean match = false;
        int size = affl.size();
        for (int i = 0; i < size; i++) {
          auxmap = affl.get(i);
          Map<String, Integer> mp = compareAff(newmap, auxmap);
          if (mp != null) {
            affl.add(i, commonServices.sortByComparator(mp, false));
            affl.remove(i + 1);
            match = true;
          }
        }
        if (!match) {
          affl.add(commonServices.sortByComparator(newmap, false));
        }

      }
    }

    JSONObject main = new JSONObject();
    JSONArray array = new JSONArray();
    List keys = new ArrayList(auxmap.keySet());
    Collections.sort(keys);
    System.out.print(keys);
    for (Map<String, Integer> af : affl) {
      JSONObject obj = new JSONObject();
      int n = 0;
      for (String a : af.keySet()) {
        if (n < 1 && !blackl.contains(a)) {
          obj.put("name", a);
          array.add(obj);
        }
        n++;
      }
    }

    main.put("data", array);

    return main.toJSONString();
    // distservice.jaccardDistance("", "");
    // Map<String, Integer> unsortMap = new HashMap<String, Integer>();
    //  Map<String, Integer> map =commonServices.sortByComparator(unsortMap, false);

    //  return "";
  }

  public boolean validwords(String key) {
    List<String> vw = new ArrayList();
    vw.add("university");
    vw.add("universidad");
    vw.add("institute");
    vw.add("instituto");
    vw.add("department");
    vw.add("departamento");
    for (String w : vw) {
      if (key.contains(w)) {
        return true;
      }
    }

    return false;

  }

  private Map<String, Integer> compareAff(Map<String, Integer> newmap, Map<String, Integer> oldmap) {
    boolean equival = false;
    Map<String, Integer> temp = new HashMap();

    for (String key2 : oldmap.keySet()) {
      for (String key : newmap.keySet()) {
        if (distservice.jaccardDistance(key, key2) > 0.7) {
          equival = true;
          temp.put(key2, oldmap.get(key2) + 1);
        } else {
          temp.put(key, 0);
        }
      }
    }
    if (equival) {
      oldmap.putAll(temp);
    }
    return equival ? oldmap : null;
  }

  @Override
  public String getStatsbyAuthor() {
    String uri = "https://redi.cedia.edu.ec/resource/authors/UCUENCA/oai-pmh/SAQUICELA_GALARZA__VICTOR_HUGO";
    System.out.print("Entrando Metodo");
    try {
      getStatsConferencebyAuthor(uri);
      return getStatsAffbyAuthor(uri);
    } catch (MarmottaException ex) {
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
      return "error";
    }
  }

  @Override
  public void LoadStatisticsbyAuthor() {
    final Task task = taskManagerService.createSubTask("Caching statistics by Author", "Mongo Service");
    try (MongoClient client = new MongoClient(conf.getStringConfiguration("mongo.host"), conf.getIntConfiguration("mongo.port"));) {
      MongoDatabase db = client.getDatabase(MongoService.Database.NAME.getDBName());
      MongoCollection<Document> collection = db.getCollection(MongoService.Collection.STATISTICS_AUTHOR.getValue());
      collection.drop();

      List<String> queries = new ArrayList();
      queries.add("date");
      queries.add("keywords");
      queries.add("providers");
      queries.add("provenance");
      queries.add("conference");

      final String uri = "";
      String name = "";
      String fullname = "";
      final List<Map<String, Value>> authors = sparqlService.query(QueryLanguage.SPARQL, queriesService.getAuthorsCentralGraph());
      Document parse = new Document();
      task.updateTotalSteps(authors.size());
      int ints = 0;
      // final int j = 0;
      for (Map<String, Value> o : authors) {
        //  j++;
        ints++;

        final String a = o.get("a").stringValue();
        task.updateDetailMessage("Author ", a);
        final SynchronizedParse sp = new SynchronizedParse();
        BoundedExecutor threadPool = BoundedExecutor.getThreadPool(5);

        log.info("Stats {} ", a);
        log.info("Stats {}/{}. Author: '{}' ", ints, authors.size(), a);
        //task.updateDetailMessage("URI", a);
        task.updateProgress(ints);
        for (final String q : queries) {

          threadPool.submitTask(new Runnable() {
            @Override
            public void run() {

              String response;
              try {
                response = statisticsbyAuthorsQuery(a, q);
                sp.appendParse(Document.parse(response), q);

              } catch (MarmottaException ex) {
                java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
              }

            }
          });

          /*  ints++;
                    uri = a;
                    parse.append(q, Document.parse(response));

                    log.info("Stats Author {} ", uri);
                    log.info("Query {}", q);

                    task.updateProgress(ints);*/
        }
        threadPool.end();
        Document authorp = sp.getDoc();
        authorp.append("_id", a);
        // parse.append("name", name);
        // parse.append("fullname", fullname);
        collection.insertOne(authorp);
      }
      taskManagerService.endTask(task);
      // loadStadistics(MongoService.Collection.STATISTICS.getValue(), queries);
    } catch (MarmottaException ex) {
      log.error("erro" + ex);
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.INFO, null, ex);
    } catch (InterruptedException ex) {
      java.util.logging.Logger.getLogger(PopulateMongoImpl.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private String statisticsbyAuthorsQuery(String uri, String query) throws MarmottaException {
    // List <Map<String,String>>   
    switch (query) {
      case "date":
        return this.getStatsAuthorbyPubDate(uri);
      case "keywords":
        return this.getStatsRelevantKbyAuthor(uri);
      case "providers":
        return this.getStatsProvbyAuthor(uri);
      case "provenance":
        return this.getStatsAffbyAuthor(uri);
      case "conference":
        return this.getStatsConferencebyAuthor(uri);
      default:
        return null;

    }

  }

  class SynchronizedParse {

    private Document queryparse = new Document();

    // Synchronized Method 
    public synchronized void appendParse(Document d, String q) {
      queryparse.append(q, d);
    }

    public Document getDoc() {
      return queryparse;
    }
  }
}
