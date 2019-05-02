/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.marmotta.ucuenca.wk.commons.service;

import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.openrdf.model.Value;

/**
 *
 * @author FernandoBac
 */
public interface CommonsServices {

    String removeAccents(String input);

    String cleanNameArticles(String name);

    /**
     * Return true or false if object is a URI
     *
     * @param object
     * @return
     */
    Boolean isURI(String object);

    String getMD5(String input);

    String readPropertyFromFile(String file, String property);

    String getIndexedPublicationsFilter(String text);

    JSONObject getObjectfromResult(Map<String, Value> map) throws JSONException;

    String listmapTojson(List<Map<String, Value>> list);

    String mapTojson(Map<String, String> map);

    Object getHttpJSON(String query);
    
    Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap, final boolean order);

}
