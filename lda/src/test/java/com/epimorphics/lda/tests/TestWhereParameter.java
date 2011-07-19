/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.epimorphics.lda.core.CallContext;
import com.epimorphics.lda.core.NamedViews;
import com.epimorphics.lda.query.APIQuery;
import com.epimorphics.lda.query.ContextQueryUpdater;
import com.epimorphics.lda.query.QueryArgumentsImpl;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.util.RDFUtils;

public class TestWhereParameter 
	{
	static final String defaultQuery = "?item ?__p ?__v .";
	
	@Test public void testAddWhereParameter()
		{    
        ShortnameService sns = TestSelectParameter.makeSNS();
        APIQuery q = new APIQuery(sns);		
    	QueryArgumentsImpl qa = new QueryArgumentsImpl(q);
        ContextQueryUpdater x = new ContextQueryUpdater( (CallContext) null, NamedViews.noNamedViews, sns, q, qa );
        qa.updateQuery();
        String theBaseQuery = q.assembleSelectQuery( RDFUtils.noPrefixes );
        String theWhereClause = "?p rdf:equals 17";
        x.handleReservedParameters( null, null, "_where", theWhereClause );
    //
    // this is horrid -- want something better later. too dependent on
    // string arithmetic.
    //
        String theUpdatedQuery = q.assembleSelectQuery( RDFUtils.noPrefixes );
//        System.err.println( ">> the updated query: " + theUpdatedQuery );
//        System.err.println( ">> the base query: " + theBaseQuery );
        assertTrue( theUpdatedQuery.contains( theWhereClause ) );
        // Commented out. The base case has a dumy ?x ?p ?o clause but this should not be present in the
        // case of a where clause
		String pruned = theUpdatedQuery.replace( theWhereClause, "" ).replaceAll( "[ \n]", "" );
		String other = theBaseQuery.replace(defaultQuery, "").replaceAll( "[ \n]", "" );
		if (!pruned.equals( other))
			{
			fail( "the updated query " + theUpdatedQuery + " isn't just the base query with different triples." );
			}
		}
	}
