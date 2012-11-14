package com.epimorphics.lda.views.tests;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.epimorphics.jsonrdf.utils.ModelIOUtils;
import com.epimorphics.lda.apispec.tests.SpecUtil;
import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.core.APIEndpoint;
import com.epimorphics.lda.core.APIEndpointImpl;
import com.epimorphics.lda.core.APIResultSet;
import com.epimorphics.lda.routing.MatchSearcher;
import com.epimorphics.lda.specs.APISpec;
import com.epimorphics.lda.support.Controls;
import com.epimorphics.lda.support.MultiMap;
import com.epimorphics.lda.support.Times;
import com.epimorphics.lda.tests_support.MakeData;
import com.epimorphics.util.Triad;
import com.epimorphics.util.URIUtils;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

public class TestAPITemplate {
	
	@Test public void testTemplate() {
		
		String eh_prefix = "@prefix : <eh:/>.";
		
		Model specModel = modelFrom
			(
			eh_prefix
			, "@prefix api:     <http://purl.org/linked-data/api/vocab#> ."
			, "@prefix dc:      <http://purl.org/dc/elements/1.1/> ."
			, "@prefix geo:     <http://www.w3.org/2003/01/geo/wgs84_pos#> ."
			, "@prefix owl:     <http://www.w3.org/2002/07/owl#> ."
			, "@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ."
			, "@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> ."
			, "@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> ."
			, "@prefix foaf:    <http://xmlns.com/foaf/0.1/> ."
			, "@prefix school:  <http://education.data.gov.uk/def/school/> ."
			, "@prefix skos:    <http://www.w3.org/2004/02/skos/core#> ."
		//
			, ":root a api:API"
			, "; api:sparqlEndpoint <here:dataPart>"
			, "; api:endpoint :ep"
			, "."
		//
			, ":ep a api:ListEndpoint"
			, "; api:uriTemplate '/this'"
			, "; api:selector [ api:filter 'type=Item' ]" 
			, "; api:defaultViewer [api:template '?item :predicate ?v']"
			, "."
		//
			, "<here:dataPart> :elements :A, :B"
			, "."
		//
			, ":A a :Item; :predicate :X."
			, ":B a :Item; :catiprede :Y."
		//
			, ":Item a rdfs:Class"
			, "."
			);

		Resource root = specModel.createResource( specModel.expandPrefix( ":root" ) );
		
		APISpec spec = SpecUtil.specFrom( root );
				
		APIEndpoint ep = new APIEndpointImpl( spec.getEndpoints().get(0) );
		Bindings epBindings = ep.getSpec().getBindings();
		MultiMap<String, String> map = MakeData.parseQueryString( "" );
		URI ru = URIUtils.newURI( "/this" );
		Bindings cc = Bindings.createContext( bindTemplate( epBindings, "/this", "/path", map ), map );
		Triad<APIResultSet, String, Bindings> resultsAndFormat = ep.call( controls, ru, cc );
		Model rsm = resultsAndFormat.a.getModel();
		
		Model obtained = ModelFactory.createDefaultModel();
		obtained.add( rsm );
		
		assertHas( obtained, eh_prefix, ":A :predicate :X." );
		assertHasnt( obtained, eh_prefix, ":B :catiprede :Y." );		
	}	
	
	private void assertHas( Model obtained, String ... lines ) {
		Model wanted = modelFrom( lines );
		for (Statement s: wanted.listStatements().toList())
			if (!obtained.contains( s ))
				fail("missing required statement: " + s );
	}	
	
	private void assertHasnt( Model obtained, String ... lines ) {
		Model wanted = modelFrom( lines );
		for (Statement s: wanted.listStatements().toList())
			if (obtained.contains( s ))
				fail("has prohibited statement: " + s );
	}

	private Bindings bindTemplate( Bindings epBindings, String template, String path, MultiMap<String, String> qp ) {
		MatchSearcher<String> ms = new MatchSearcher<String>();
		ms.register( template, "IGNORED" );
		Map<String, String> bindings = new HashMap<String, String>();
		ms.lookup( bindings, path, qp );
		return epBindings.updateAll( bindings ); 
	}
	
	static final Controls controls = new Controls( true, new Times() );

	private Model modelFrom(String ... lines) {
		StringBuilder ttl = new StringBuilder();
		for (String line: lines) ttl.append( line ).append( '\n' );
		return ModelIOUtils.modelFromTurtle( ttl.toString() );
	}
	
	

}
