/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors. 
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.dna.jcr;

import org.jboss.dna.graph.property.Name;
import org.jboss.dna.graph.property.basic.BasicName;

/**
 * @author Randall Hauch
 */
public class DnaLexicon extends org.jboss.dna.graph.DnaLexicon {

    public static final Name NAMESPACES = new BasicName(Namespace.URI, "namespaces");
    public static final Name NAMESPACE = new BasicName(Namespace.URI, "namespace");
    public static final Name NODE_DEFINITON = new BasicName(Namespace.URI, "nodeDefinition");
    public static final Name ROOT = new BasicName(Namespace.URI, "root");
    public static final Name SYSTEM = new BasicName(Namespace.URI, "system");
    public static final Name URI = new BasicName(Namespace.URI, "uri");

    /**
     * Mixin type that indicates the node contains an xmltext node which holds xmltext from document view import (see JCR 1.0
     * specification section 7.3.2). This node has a child node named {@link DnaLexicon#XML_TEXT} of type
     * {@link DnaLexicon#XML_TEXT_TYPE}.
     */
    public static final Name XML_CONTENT = new BasicName(Namespace.URI, "xmlContent");

    /**
     * Name of node type that holds xmltext from document view import (see JCR 1.0 specification section 7.3.2). It is defined in
     * the node type named {@link DnaLexicon#XML_CONTENT}.
     */
    public static final Name XML_TEXT_TYPE = new BasicName(Namespace.URI, "xmlText");

    /**
     * Name of the child node that holds xmltext from document view import (see JCR 1.0 specification section 7.3.2). This is the
     * name of the child node of the {@link DnaLexicon#XML_CONTENT} mixin type. By definition, this node has a required primary
     * type of {@link DnaLexicon#XML_TEXT_TYPE}.
     */
    public static final Name XML_TEXT = new BasicName(Namespace.URI, "xmltext");
}
