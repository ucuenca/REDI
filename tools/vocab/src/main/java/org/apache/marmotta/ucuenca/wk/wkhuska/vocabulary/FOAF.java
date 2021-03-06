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
 * Namespace FOAF.
 *
 * @author Jose Segarra <jose.segarraf@ucuenca.ec>
 */
public class FOAF {

    public static final String NAMESPACE = "http://xmlns.com/foaf/0.1/";
    public static final String PREFIX = "foaf";
    public static final URI ORGANIZATION;
    public static final URI NAME;
    

    static {
        ValueFactory factory = ValueFactoryImpl.getInstance();
        ORGANIZATION  = factory.createURI(NAMESPACE, "Organization");
        NAME = factory.createURI(NAMESPACE, "name");
        
    }
}
