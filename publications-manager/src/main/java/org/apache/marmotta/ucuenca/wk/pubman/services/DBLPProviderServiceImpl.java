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
package org.apache.marmotta.ucuenca.wk.pubman.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.apache.marmotta.commons.sesame.model.ModelCommons;
import org.apache.marmotta.kiwi.model.rdf.KiWiUriResource;
import org.apache.marmotta.ldclient.exception.DataRetrievalException;
import org.apache.marmotta.ldclient.model.ClientConfiguration;
import org.apache.marmotta.ldclient.model.ClientResponse;
import org.apache.marmotta.ldclient.services.ldclient.LDClient;
import org.apache.marmotta.platform.core.exception.InvalidArgumentException;
import org.apache.marmotta.platform.core.exception.MarmottaException;
import org.apache.marmotta.platform.sparql.api.sparql.SparqlService;
import org.apache.marmotta.ucuenca.wk.commons.service.ConstantService;
import org.apache.marmotta.ucuenca.wk.commons.service.QueriesService;

import org.apache.marmotta.ucuenca.wk.pubman.api.SparqlFunctionsService;

import org.apache.marmotta.ucuenca.wk.pubman.exceptions.PubException;
import org.apache.marmotta.ucuenca.wk.commons.impl.Constant;
import org.apache.marmotta.ucuenca.wk.commons.service.ComparisonNames;
import org.apache.marmotta.ucuenca.wk.pubman.api.DBLPProviderService;

import org.openrdf.model.Model;
import org.openrdf.model.Statement;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.QueryResults;

import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.GraphQueryResult;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.openrdf.model.Value;
import org.openrdf.query.UpdateExecutionException;
import org.semarglproject.vocab.OWL;

/**
 * Default Implementation of {@link PubVocabService}
 */
@ApplicationScoped
public class DBLPProviderServiceImpl implements DBLPProviderService, Runnable {

    @Inject
    private Logger log;

    @Inject
    private QueriesService queriesService;

    @Inject
    private ConstantService pubVocabService;

    @Inject
    private SparqlFunctionsService sparqlFunctionsService;

    @Inject
    private ComparisonNames comparisonNames;

    private String namespaceGraph = "http://ucuenca.edu.ec/wkhuska/";
    private String authorGraph = namespaceGraph + "authors";
    private String endpointsGraph = namespaceGraph + "endpoints";
    private String externalAuthorGraph = namespaceGraph + "externalauthors";

    private final Constant con = new Constant();

    private int processpercent = 0;

    /* graphByProvider
     Graph to save publications data by provider
     Example: http://ucuenca.edu.ec/wkhuska/dblp
     */
    private String graphByProviderNS = namespaceGraph + "provider/";

    @Inject
    private SparqlService sparqlService;

    @Override
    public String runPublicationsTaskImpl(String param) {

        try {

            String providerGraph = "";
            //String getAuthorsQuery = queriesService.getAuthorsQuery();
            String getGraphsListQuery = queriesService.getGraphsQuery();
            List<Map<String, Value>> resultGraph = sparqlService.query(QueryLanguage.SPARQL, getGraphsListQuery);
            /* FOR EACH GRAPH*/

            for (Map<String, Value> map : resultGraph) {
                providerGraph = map.get("grafo").toString();
                KiWiUriResource providerGraphResource = new KiWiUriResource(providerGraph);

                if (providerGraph.contains("provider")) {

                    Properties propiedades = new Properties();
                    InputStream entrada = null;
                    Map<String, String> mapping = new HashMap<String, String>();
                    try {
                        ClassLoader classLoader = getClass().getClassLoader();
                        //File file = new File(classLoader.getResource("DBLPProvider.properties").getFile());

                        entrada = classLoader.getResourceAsStream(providerGraphResource.getLocalName() + ".properties");
                        // cargamos el archivo de propiedades
                        propiedades.load(entrada);

                        for (String source : propiedades.stringPropertyNames()) {
                            String target = propiedades.getProperty(source);
                            mapping.put(source.replace("..", ":"), target.replace("..", ":"));
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        if (entrada != null) {
                            try {
                                entrada.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    List<Map<String, Value>> resultPublications = sparqlService.query(QueryLanguage.SPARQL, queriesService.getPublicationsQuery(providerGraph));
                    for (Map<String, Value> pubresource : resultPublications) {
                        String authorResource = pubresource.get("authorResource").toString();
                        String publicationResource = pubresource.get("publicationResource").toString();
                        String publicationProperty = pubVocabService.getPubProperty();

                        //verificar existencia de la publicacion y su author sobre el grafo general
                        String askTripletQuery = queriesService.getAskQuery(authorGraph, authorResource, publicationProperty, publicationResource);
                        if (!sparqlService.ask(QueryLanguage.SPARQL, askTripletQuery)) {
                            String insertPubQuery = buildInsertQuery(authorGraph, authorResource, publicationProperty, publicationResource);
                            try {
                                sparqlService.update(QueryLanguage.SPARQL, insertPubQuery);
                            } catch (MalformedQueryException ex) {
                                log.error("Malformed Query:  " + insertPubQuery);
                            } catch (UpdateExecutionException ex) {
                                log.error("Update Query :  " + insertPubQuery);
                            } catch (MarmottaException ex) {
                                log.error("Marmotta Exception:  " + insertPubQuery);
                            }
                        }

                        List<Map<String, Value>> resultPubProperties = sparqlService.query(QueryLanguage.SPARQL, queriesService.getPublicationsPropertiesQuery(providerGraph, publicationResource));
                        for (Map<String, Value> pubproperty : resultPubProperties) {
                            String nativeProperty = pubproperty.get("publicationProperties").toString();
                            if (mapping.get(nativeProperty) != null) {

                                String newPublicationProperty = mapping.get(nativeProperty);
                                String publicacionPropertyValue = pubproperty.get("publicationPropertyValue").toString();
                                String insertPublicationPropertyQuery = buildInsertQuery(authorGraph, publicationResource, newPublicationProperty, publicacionPropertyValue);

                                try {
                                    sparqlService.update(QueryLanguage.SPARQL, insertPublicationPropertyQuery);
                                } catch (MalformedQueryException ex) {
                                    log.error("Malformed Query:  " + insertPublicationPropertyQuery);
                                } catch (UpdateExecutionException ex) {
                                    log.error("Update Query:  " + insertPublicationPropertyQuery);
                                } catch (MarmottaException ex) {
                                    log.error("Marmotta Exception:  " + insertPublicationPropertyQuery);
                                }
                            }
                        }
                        //compare properties with the mapping and insert new properties
                        //mapping.get(map)
                    }
                }

                //in this part, for each graph
            }
            return "Los datos de las publicaciones se han cargado exitosamente.";
        } catch (InvalidArgumentException ex) {
            return "error:  " + ex;
        } catch (MarmottaException ex) {
            return "error:  " + ex;
        }
    }

    @Override
    public String runPublicationsProviderTaskImpl(String param) {
        try {

            //new AuthorVersioningJob(log).proveSomething();
            ClientConfiguration conf = new ClientConfiguration();
            //conf.addEndpoint(new DBLPEndpoint());
            LDClient ldClient = new LDClient(conf);

            int allMembers = 0;
            String getAllAuthorsDataQuery = queriesService.getAuthorsDataQuery(authorGraph, endpointsGraph);

            // TupleQueryResult result = sparqlService.query(QueryLanguage.SPARQL, getAuthors);
            String nameToFind = "";
            String authorResource = "";
            int priorityToFind = 0;
            List<Map<String, Value>> resultAllAuthors = sparqlService.query(QueryLanguage.SPARQL, getAllAuthorsDataQuery);

            /*To Obtain Processed Percent*/
            int allPersons = resultAllAuthors.size();
            int processedPersons = 0;

            String NS_DBLP = "http://rdf.dblp.com/ns/search/";
            RepositoryConnection conUri = null;
            ClientResponse response = null;

            Properties propiedades = new Properties();
            InputStream entrada = null;
            Map<String, String> mapping = new HashMap<String, String>();
            try {
                ClassLoader classLoader = getClass().getClassLoader();
                entrada = classLoader.getResourceAsStream("updatePlatformProcessConfig.properties");
                propiedades.load(entrada);
                for (String source : propiedades.stringPropertyNames()) {
                    String target = propiedades.getProperty(source);
                    mapping.put(source, target);

                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (entrada != null) {
                    try {
                        entrada.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            boolean proccesAllAuthors = Boolean.parseBoolean(mapping.get("proccesAllAuthors").toString());

            for (Map<String, Value> map : resultAllAuthors) {
                processedPersons++;
                log.info("Autores procesados con DBLP: " + processedPersons + " de " + allPersons);
                authorResource = map.get("subject").stringValue();
                String firstName = map.get("fname").stringValue();
                String lastName = map.get("lname").stringValue();
                priorityToFind = 1;
                boolean ask = false;
                if (!proccesAllAuthors) {
                    String askTripletQuery = queriesService.getAskProcessAlreadyAuthorProvider(con.getDBLPGraph(), authorResource);

                    try {
                        ask = sparqlService.ask(QueryLanguage.SPARQL, askTripletQuery);
                        if (ask) {
                            continue;
                        }
                    } catch (MarmottaException ex) {
                        log.info("Marmotta Exception: Characters special in ask to author resource: " + askTripletQuery);

                    } catch (Exception e) {
                        log.info("Characters special in ask to author resource: " + askTripletQuery);

                    }

                }
                do {
                    try {
                        boolean existNativeAuthor = false;
                        allMembers = 0;
                        nameToFind = priorityFindQueryBuilding(priorityToFind, firstName, lastName);

                        boolean dataretrievee = false;//( Data Retrieve Exception )

                        if (!proccesAllAuthors) {
                            existNativeAuthor = sparqlService.ask(QueryLanguage.SPARQL, queriesService.getAskResourceQuery(con.getDBLPGraph(), NS_DBLP + nameToFind));
                        }
                        if (!existNativeAuthor) {

                            try {
                                response = ldClient.retrieveResource(NS_DBLP + nameToFind);
                                dataretrievee = true;
                            } catch (DataRetrievalException e) {
                                log.error("Data Retrieval Exception: " + e);
                                dataretrievee = false;
//                               
                            }

                            if (response.getHttpStatus() == 503) {
                                log.error("ErrorCode: " + response.getHttpStatus());
                            }
                        }

                        String nameEndpointofPublications = ldClient.getEndpoint(NS_DBLP + nameToFind).getName();
                        String providerGraph = graphByProviderNS + nameEndpointofPublications.replace(" ", "");
                        if (dataretrievee)//if the resource data were recovered
                        {
//                            Model model = response.getData();
//                            FileOutputStream out = new FileOutputStream("C:\\Users\\Satellite\\Desktop\\" + nameToFind.replace("?", "_") + "_test.ttl");
//                            RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
//                            try {
//                                writer.startRDF();
//                                for (Statement st : model) {
//                                    writer.handleStatement(st);
//                                }
//                                writer.endRDF();
//                            } catch (RDFHandlerException e) {
//                                // oh no, do something!
//                            }
                            //Save register of serach
                            String InsertQueryOneOf = buildInsertQuery(providerGraph, NS_DBLP + nameToFind, OWL.ONE_OF, authorResource);
                            updatePub(InsertQueryOneOf);

                            conUri = ModelCommons.asRepository(response.getData()).getConnection();
                            conUri.begin();
                            String authorNativeResource = null;
                            //verifying the number of persons retrieved. if it has recovered more than one persons then the filter is changed and search anew,
                            String getMembersQuery = queriesService.getMembersQuery();
                            TupleQueryResult membersResult = conUri.prepareTupleQuery(QueryLanguage.SPARQL, getMembersQuery).evaluate();
                            //  allMembers = Iterations.asList(membersResult).size();
                            while (membersResult.hasNext()) {
                                allMembers++;
                                BindingSet bindingCount = membersResult.next();
                                authorNativeResource = bindingCount.getValue("members").toString();
                                existNativeAuthor = sparqlService.ask(QueryLanguage.SPARQL, queriesService.getAskResourceQuery(providerGraph, authorNativeResource));
                            }
                            //the author data was already loaded into the repository, only a sameAs property is associated 
                            if (allMembers == 1 && existNativeAuthor) {
                                //insert sameAs triplet    <http://190.15.141.102:8080/dspace/contribuidor/autor/SaquicelaGalarza_VictorHugo> owl:sameAs <http://dblp.org/pers/xr/s/Saquicela:Victor> 
                                String sameAsInsertQuery = buildInsertQuery(providerGraph, authorResource, "http://www.w3.org/2002/07/owl#sameAs", authorNativeResource);
                                updatePub(sameAsInsertQuery);
                            }
                            /**
                             * Exception to avoid authorNativeResource equal to
                             * null.
                             *
                             *
                             */
                            try {

                                String dblpfullname = authorNativeResource.substring(authorNativeResource.lastIndexOf('/') + 1);
                                String localfullname = lastName + ":" + firstName;

                                if (allMembers == 1 && !existNativeAuthor
                                        && comparisonNames.syntacticComparison("local", localfullname, "dblp", dblpfullname)) {
                                    priorityToFind = 5;
                                    //SPARQL obtain all publications of author
                                    String getPublicationsFromProviderQuery = queriesService.getPublicationFromProviderQuery();
                                    TupleQuery pubquery = conUri.prepareTupleQuery(QueryLanguage.SPARQL, getPublicationsFromProviderQuery); //
                                    TupleQueryResult tripletasResult = pubquery.evaluate();

                                    while (tripletasResult.hasNext()) {
                                        BindingSet tripletsResource = tripletasResult.next();
                                        authorNativeResource = tripletsResource.getValue("authorResource").toString();
                                        String publicationResource = tripletsResource.getValue("publicationResource").toString();
                                        String publicationProperty = tripletsResource.getValue("publicationProperty").toString();
                                        ///insert sparql query, 
                                        String publicationInsertQuery = buildInsertQuery(providerGraph, authorNativeResource, publicationProperty, publicationResource);
                                        updatePub(publicationInsertQuery);

                                        // sameAs triplet    <http://190.15.141.102:8080/dspace/contribuidor/autor/SaquicelaGalarza_VictorHugo> owl:sameAs <http://dblp.org/pers/xr/s/Saquicela:Victor> 
                                        String sameAsInsertQuery = buildInsertQuery(providerGraph, authorResource, "http://www.w3.org/2002/07/owl#sameAs", authorNativeResource);
                                        updatePub(sameAsInsertQuery);
                                    }

                                    // SPARQL to obtain all data of a publication
                                    String getPublicationPropertiesQuery = queriesService.getPublicationPropertiesQuery();
                                    TupleQuery resourcequery = conUri.prepareTupleQuery(QueryLanguage.SPARQL, getPublicationPropertiesQuery); //
                                    tripletasResult = resourcequery.evaluate();
                                    while (tripletasResult.hasNext()) {
                                        BindingSet tripletsResource = tripletasResult.next();
                                        String publicationResource = tripletsResource.getValue("publicationResource").toString();
                                        String publicationProperties = tripletsResource.getValue("publicationProperties").toString();
                                        String publicationPropertiesValue = tripletsResource.getValue("publicationPropertiesValue").toString();
                                        ///insert sparql query, 
                                        String publicationPropertiesInsertQuery = buildInsertQuery(providerGraph, publicationResource, publicationProperties, publicationPropertiesValue);
                                        //load values publications to publications resource
                                        updatePub(publicationPropertiesInsertQuery);

                                    }

                                }//end if numMembers=1
                            } catch (Exception e) {
                                log.info("ERROR in full name:" + authorNativeResource);
                            } finally {

                                conUri.commit();
                                conUri.close();
                            }
                        }
                    } catch (QueryEvaluationException | MalformedQueryException | RepositoryException ex) {
                        log.error("Evaluation Exception: " + ex);
                    } catch (Exception e) {
                        log.error("ioexception " + e.toString());
                    }
                    priorityToFind++;
                } while (allMembers != 1 && priorityToFind < 5);//end do while
                //** end View Data
                printPercentProcess(processedPersons, allPersons, "DBLP");
            }
            return "True for publications";
        } catch (MarmottaException ex) {
            log.error("Marmotta Exception: " + ex);
        }

        return "fail";
    }

    @Override
    public JsonArray SearchAuthorTaskImpl(String uri
    ) {
        JsonParser parser = new JsonParser();
        String scopusconcat = "?apiKey=a3b64e9d82a8f7b14967b9b9ce8d513d&view=ENHANCED&httpAccept=application/rdf%2Bxml";
        try {
//new AuthorVersioningJob(log).proveSomething();
            ClientConfiguration conf = new ClientConfiguration();
            //conf.addEndpoint(new DBLPEndpoint());
            LDClient ldClient = new LDClient(conf);
            String nativeauthor = uri;
            //ClientResponse response = ldClient.retrieveResource("http://rdf.dblp.com/ns/m.0wqhskn");

            if (uri.contains("elsevier")) // SCOPUS CASE
            {
                uri = uri.concat(scopusconcat);
            } else {//DBLP CASE
                uri = uri.replace("\"", "");
            }

            String providerName = ldClient.getEndpoint(uri).getName();
            Properties propiedades = new Properties();
            InputStream entrada = null;
            Map<String, String> mapping = new HashMap<String, String>();
            ClassLoader classLoader = getClass().getClassLoader();
            //File file = new File(classLoader.getResource("DBLPProvider.properties").getFile());
            entrada = classLoader.getResourceAsStream(providerName.replace(" ", "") + ".properties");
            // cargamos el archivo de propiedades
            propiedades.load(entrada);
            for (String source : propiedades.stringPropertyNames()) {
                String target = propiedades.getProperty(source);
                mapping.put(source.replace("..", ":"), target.replace("..", ":"));
            }
            RepositoryConnection conUri = null;
            ClientResponse response = null;
            log.info("Buscando Informacion de: " + uri);
            try {

                try {
                    response = ldClient.retrieveResource(uri);
                } catch (DataRetrievalException e) {
                    log.error("Data Retrieval Exception: " + e);
                }
                if (response.getHttpStatus() == 503) {
                    log.error("ErrorCode: " + response.getHttpStatus());
                }
                conUri = ModelCommons.asRepository(response.getData()).getConnection();
                conUri.begin();
                //SPARQL obtain all publications of author
                String getExternalAuthorDataQuery = " PREFIX dc: <http://purl.org/dc/elements/1.1/> "
                        + " PREFIX dct: <http://purl.org/dc/terms/> "
                        + " PREFIX bibo: <http://purl.org/ontology/bibo/> "
                        + " PREFIX foaf: <http://xmlns.com/foaf/0.1/> "
                        + " CONSTRUCT  { <" + nativeauthor + "> foaf:publications ?uripub. "
                        + " ?uripub  a bibo:Document. ?uripub bibo:uri ?uri. "
                        + " ?uripub  bibo:abstract ?abstract. ?uripub bibo:Quote ?keyword. "
                        + " ?uripub dct:title ?title. ?uripub dct:contributor ?coauthor. "
                        + " ?uripub bibo:numPages ?numPages. ?uripub dct:isPartOf ?isPartOf. ?uripub dct:publisher ?publisher. "
                        + " } "
                        + " WHERE "
                        + " { "
                        + " <" + nativeauthor + ">  <" + mapping.get("publicationProperty") + "> ?publication. "
                        + " ?publication <" + mapping.get("title") + "> ?title. "
                        + " OPTIONAL { ?publication <" + mapping.get("uri") + "> ?uri. } "
                        + " OPTIONAL { ?publication <" + mapping.get("abstract") + ">  ?abstract. }"
                        + " OPTIONAL { ?publication <" + mapping.get("keyword") + ">  ?keyword. }"
                        + " OPTIONAL { ?publication <" + mapping.get("contributor") + "> ?coauthor. }"
                        + " OPTIONAL { ?publication <" + mapping.get("numPages") + "> ?numPages. }"
                        + " OPTIONAL { ?publication <" + mapping.get("isPartOf") + "> ?isPartOf. }"
                        + " OPTIONAL { ?publication <" + mapping.get("publisher") + "> ?publisher. }"
                        + " BIND (REPLACE(?title,\" \", \"_\",\"i\") as ?newtitle) "
                        + " BIND (IRI(CONCAT(\"http://ucuenca.edu.ec/wkhuska/publication/\",?newtitle)) as ?uripub) "
                        + " } LIMIT 170 ";
                GraphQueryResult graphQueryResult = conUri.prepareGraphQuery(QueryLanguage.SPARQL, getExternalAuthorDataQuery).evaluate();
                Model resultModel = QueryResults.asModel(graphQueryResult);
                //Getting data in JSONLD format 
                StringWriter writerdata = new StringWriter();
                RDFWriter writerjld = Rio.createWriter(RDFFormat.JSONLD, writerdata);
                try {
                    writerjld.startRDF();
                    for (Statement st : resultModel) {
                        String subjet = st.getSubject().stringValue();
                        String predicate = st.getPredicate().stringValue();
                        String object = st.getObject().stringValue();
                        String querytoInsert = buildInsertQuery(externalAuthorGraph, subjet, predicate, object);
                        updatePub(querytoInsert);
                        writerjld.handleStatement(st);
                    }
                    writerjld.endRDF();
                } catch (RDFHandlerException e) {
                    // oh no, do something!
                }
                conUri.commit();
                conUri.close();
                return parser.parse(writerdata.toString()).getAsJsonArray();

            } catch (QueryEvaluationException | MalformedQueryException | RepositoryException ex) {
                log.error("Evaluation Exception: " + ex);
            } catch (Exception e) {
                log.error("ioexception " + e.toString());
            }
            //** end View Data
        } catch (Exception ex) {
            log.error("Marmotta Exception: " + ex);
        }
        return parser.parse(" [{\"Fail\":\"Any Data\"}]").getAsJsonArray();
    }

    /**
     * Syntactic Disambiguation by FUll NAME
     *
     */
//    public boolean syntacticDisambiguation(String source, String target)
//    {
//        
//        return 
//    }
    public String priorityFindQueryBuilding(int priority, String firstName, String lastName) {
        String[] fnamelname = {"", "", "", "", ""};
        /**
         * fnamelname[0] is a firstName A, fnamelname[1] is a firstName B
         * fnamelname[2] is a lastName A, fnamelname[3] is a lastName B
         *
         */
        String nameProcess = "";
        for (String name : (firstName + " " + lastName).split(" ")) {
            if (name.length() > 1) {
                nameProcess += name + " ";
            }
        }
        if (nameProcess.split(" ").length > 2) {
            for (int i = 0; i < firstName.split(" ").length; i++) {
                fnamelname[i] = firstName.split(" ")[i];
            }

            for (int i = 0; i < lastName.split(" ").length; i++) {
                fnamelname[i + 2] = lastName.split(" ")[i];
            }

            switch (priority) {
//            case 5:
//                return fnamelname[3];
                case 1:
                    return fnamelname[0] + "_" + fnamelname[2];
                case 3:
                    return fnamelname[1] + "_" + fnamelname[2] + "_" + fnamelname[3];
                case 2:
                    return fnamelname[0] + "_" + fnamelname[2] + "_" + fnamelname[3];
                case 4:
                    return fnamelname[0] + "_" + fnamelname[1] + "_" + fnamelname[2] + "_" + fnamelname[3];
            }

        } else {
            for (int i = 0; i < firstName.split(" ").length; i++) {
                fnamelname[i] = firstName.split(" ")[i];
            }

            for (int i = 0; i < lastName.split(" ").length; i++) {
                fnamelname[i + 1] = lastName.split(" ")[i];
            }

            return fnamelname[0] + "_" + fnamelname[1];

        }
        return "";
    }

    /*
     *   UPDATE - with SPARQL MODULE, to load triplet in marmotta plataform
     *   
     */
    public String updatePub(String querytoUpdate) {

        try {
            sparqlFunctionsService.updatePub(querytoUpdate);
        } catch (PubException ex) {
            log.error("No se pudo insertar: " + querytoUpdate);
            //         java.util.logging.Logger.getLogger(PubVocabServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "Correcto";

    }

    /*
     * 
     * @param contAutoresNuevosEncontrados
     * @param allPersons
     * @param endpointName 
     */
    public void printPercentProcess(int processedPersons, int allPersons, String provider) {

        if ((processedPersons * 100 / allPersons) != processpercent) {
            processpercent = processedPersons * 100 / allPersons;
            log.info("Procesado el: " + processpercent + " % de " + provider);
        }
    }

    //construyendo sparql query insert 
    public String buildInsertQuery(String grapfhProv, String sujeto, String predicado, String objeto) {
        if (queriesService.isURI(objeto)) {
            return queriesService.getInsertDataUriQuery(grapfhProv, sujeto, predicado, objeto);
        } else {
            return queriesService.getInsertDataLiteralQuery(grapfhProv, sujeto, predicado, objeto);
        }
    }

    @Override
    public void run() {
        runPublicationsProviderTaskImpl("uri");
    }

}
