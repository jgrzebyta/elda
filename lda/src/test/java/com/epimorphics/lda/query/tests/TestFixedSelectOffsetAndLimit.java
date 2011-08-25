/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/
package com.epimorphics.lda.query.tests;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.epimorphics.lda.bindings.VarValues;
import com.epimorphics.lda.core.CallContext;
import com.epimorphics.lda.core.MultiMap;
import com.epimorphics.lda.core.NamedViews;
import com.epimorphics.lda.query.APIQuery;
import com.epimorphics.lda.query.ContextQueryUpdater;
import com.epimorphics.lda.query.QueryArgumentsImpl;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.tests.FakeNamedViews;
import com.epimorphics.lda.tests.SNS;
import com.epimorphics.lda.tests_support.MakeData;
import com.epimorphics.util.Util;
import com.hp.hpl.jena.shared.PrefixMapping;

public class TestFixedSelectOffsetAndLimit {

	@Test public void ensureOffsetAndLimitApplyToFixedSelect() {
		ensureOffsetAndLimit( "SELECTION OFFSET 100 LIMIT 10", "_select=SELECTION&_page=10" );
		ensureOffsetAndLimit( "SELECTION OFFSET 20 LIMIT 2", "_select=SELECTION&_page=10&_pageSize=2" );
		ensureOffsetAndLimit( "SELECTION OFFSET 21 LIMIT 3", "_select=SELECTION&_page=7&_pageSize=3" );
	}

	private void ensureOffsetAndLimit(String expected, String queryArgs) {
		MultiMap<String, String> qp = MakeData.parseQueryString( queryArgs );
		VarValues bindings = MakeData.variables( "" );
		CallContext cc = CallContext.createContext( Util.newURI("my:URI"), qp, bindings );
		NamedViews nv = new FakeNamedViews();
		ShortnameService sns = new SNS( "" );
		APIQuery aq = new APIQuery( sns );
		QueryArgumentsImpl qa = new QueryArgumentsImpl(aq);
		ContextQueryUpdater cq = new ContextQueryUpdater( ContextQueryUpdater.ListEndpoint, cc, nv, sns, aq, qa );
		cq.updateQueryAndConstructView( aq.deferredFilters );
		qa.updateQuery();
		String q = aq.assembleSelectQuery( PrefixMapping.Factory.create() );
		assertMatches( expected, q );
	}

	private void assertMatches(String pattern, String subject) {
		if (!subject.matches( pattern ))
			fail( "subject does not match pattern '" + pattern + "': " + subject );
	}
}