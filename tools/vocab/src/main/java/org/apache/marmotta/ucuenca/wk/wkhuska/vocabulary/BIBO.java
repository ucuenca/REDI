/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.marmotta.ucuenca.wk.wkhuska.vocabulary;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;

/**
 * Namespace BIBO.
 *
 * @author Xavier Sumba <xavier.sumba93@ucuenca.ec>
 */
public class BIBO {

    public static final String NAMESPACE = "http://purl.org/ontology/bibo/";
    public static final String PREFIX = "bibo";
    public static final URI ABSTRACT;
    public static final URI PAGES;
    public static final URI CONFERENCE;
    public static final URI VOLUME;
    public static final URI ISSUE;
    public static final URI BOOK;
    public static final URI URI;
    public static final URI JOURNAL;
    public static final URI DOCUMENT;
    public static final URI ACADEMIC_ARTICLE;
    public static final URI QUOTE;
    public static final URI DOI;
    public static final URI PAGE_START;
    public static final URI PAGE_END;
    public static final URI ISSN;
    public static final URI ISBN;

    static {
        ValueFactory factory = ValueFactoryImpl.getInstance();
        BOOK = factory.createURI(NAMESPACE, "Book");
        CONFERENCE = factory.createURI(NAMESPACE, "Conference");
        JOURNAL = factory.createURI(NAMESPACE, "Journal");
        DOCUMENT = factory.createURI(NAMESPACE, "Document");
        ACADEMIC_ARTICLE = factory.createURI(NAMESPACE, "AcademicArticle");
        QUOTE = factory.createURI(NAMESPACE, "Quote");
        
        ABSTRACT = factory.createURI(NAMESPACE, "abstract");
        PAGES = factory.createURI(NAMESPACE, "pages");
        VOLUME = factory.createURI(NAMESPACE, "volume");
        ISSUE = factory.createURI(NAMESPACE, "issue");
        URI = factory.createURI(NAMESPACE, "uri");
        DOI = factory.createURI(NAMESPACE, "doi");
        PAGE_START = factory.createURI(NAMESPACE, "pageStart");
        PAGE_END = factory.createURI(NAMESPACE, "pageEnd");
        ISSN = factory.createURI(NAMESPACE, "issn");
        ISBN = factory.createURI(NAMESPACE, "isbn");
        
    }
}
