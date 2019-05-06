/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.marmotta.ucuenca.wk.pubman.utils;

import java.util.ArrayList;
import java.util.List;
import org.apache.marmotta.platform.core.exception.MarmottaException;
import org.apache.marmotta.ucuenca.wk.commons.disambiguation.Person;
import org.apache.marmotta.ucuenca.wk.commons.disambiguation.Provider;
import org.apache.marmotta.ucuenca.wk.commons.util.GraphDB;
import org.junit.Test;
import org.openrdf.repository.RepositoryException;

/**
 *
 * @author cedia
 */
public class DisambiguationTest {

    @Test
    public void compareNames() {
        Person a = new Person();
        a.Name = new ArrayList<>();
        ArrayList<String> st = new ArrayList<>();
        st.add(" CREPO");
        //st.add("WONG DE BALZAR");
        a.Name.add(st);
        Person b = new Person();
        b.Name = new ArrayList<>();
        ArrayList<String> st2 = new ArrayList<>();
        st2.add(" CRESPO");
        //st2.add("WONG DE BALZAR");
        //st2.add("De Los Reyes");
        b.Name.add(st2);
        System.out.println(a.checkName(b, true));
        
        
    }

    @Test
    public void test() throws RepositoryException, MarmottaException {
        List<Provider> Providers = new ArrayList<>();
        Providers.add(new Provider("Authors", "https://redi.cedia.edu.ec/context/authorsProvider", GraphDB.get("data").getSps()));
        Providers.add(new Provider("Scopus", "https://redi.cedia.edu.ec/context/provider/ScopusProvider", GraphDB.get("data").getSps()));

        List<Person> authors = Providers.get(0).getAuthors("https://redi.cedia.edu.ec/resource/authors/ESPE/oai-pmh/FUERTES__WALTER");
        Providers.get(0).FillData(authors);
        List<Person> candidates = Providers.get(1).getCandidates("https://redi.cedia.edu.ec/resource/authors/ESPE/oai-pmh/FUERTES__WALTER");
        Providers.get(1).FillData(candidates);
        for (Person q : authors) {
            for (Person p : candidates) {
                boolean check = q.check(p, true);
                System.err.println(check);
                System.err.println(q.URI);
                System.err.println(p.URI);
            }
        }

    }
}
