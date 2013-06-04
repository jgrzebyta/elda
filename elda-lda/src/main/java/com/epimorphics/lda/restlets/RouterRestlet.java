/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

/******************************************************************
    File:        RouterServlet.java
    Created by:  Dave Reynolds
    Created on:  31 Jan 2010
 * 
 * (c) Copyright 2010, Epimorphics Limited
 * $Id:  $
 *****************************************************************/

package com.epimorphics.lda.restlets;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.epimorphics.lda.bindings.URLforResource;
import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.core.APIEndpoint;
import com.epimorphics.lda.core.APIEndpointUtil;
import com.epimorphics.lda.core.APIResultSet;
import com.epimorphics.lda.exceptions.EldaException;
import com.epimorphics.lda.exceptions.UnknownShortnameException;
import com.epimorphics.lda.exceptions.QueryParseException;
import com.epimorphics.lda.renderers.Renderer;
import com.epimorphics.lda.renderers.Renderer.BytesOut;
import com.epimorphics.lda.routing.Match;
import com.epimorphics.lda.routing.Router;
import com.epimorphics.lda.specmanager.SpecManagerFactory;
import com.epimorphics.lda.support.Controls;
import com.epimorphics.lda.support.MultiMap;
import com.epimorphics.lda.support.Times;
import com.epimorphics.lda.support.pageComposition.Messages;
import com.epimorphics.lda.support.statistics.StatsValues;
import com.epimorphics.util.Couple;
import com.epimorphics.util.MediaType;
import com.epimorphics.util.Triad;
import com.epimorphics.util.URIUtils;
import com.hp.hpl.jena.shared.WrappedException;
import com.sun.jersey.api.NotFoundException;

/**
 * Handles all incoming API calls and routes to appropriate locations.
 * 
 * @author <a href="mailto:der@epimorphics.com">Dave Reynolds</a>
 * @version $Revision: $
*/
@Path("{path: .*}") public class RouterRestlet {

    protected static Logger log = LoggerFactory.getLogger(RouterRestlet.class);

    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String VARY = "Vary";
    public static final String ETAG = "Etag";
    public static final String LAST_MODIFIED_DATE = "Last-Modified-Date";
    
    final Router router;
    static final Map<String, Router> routers = new HashMap<String, Router>();
    
    /**
        Initialise this RouterRestlet. Happens a lot, so expensive
        initialisations should be cached. Sets the router used by
        this instance according to the appropriate LDA configs.
    */
    public RouterRestlet( @Context ServletContext con ) {
    	router = getRouterFor( con );
    }
    
    public static class Init implements ServletContextListener {
    	
	    @Override public void contextInitialized(ServletContextEvent sce) {
			getRouterFor( sce.getServletContext() );
		}
	
		@Override public void contextDestroyed(ServletContextEvent sce) {			
		}
	}
       
    /**
     	Answer a router initialised with the URI templates appropriate to
     	this context path. Such a router may already be in the routers table,
     	in which case it is used, otherwise a new router is created, initialised,
     	put in the table, and returned.
    */
     static synchronized Router getRouterFor(ServletContext con) {
    	 String givenContextPath = con.getContextPath();
    	 // log.info( "getting router for context path '" + givenContextPath + "'" );
    	 String contextPath = RouterRestletSupport.flatContextPath(givenContextPath);
    	 Router r = routers.get(contextPath);
    	 if (r == null) {    	 
    		 log.info( "creating router for context path '" + givenContextPath + "'" );
    		 r = RouterRestletSupport.createRouterFor( con );
    		 routers.put(contextPath, r );
    	 }
    	 return r;
     }

	public Match getMatch( String path, MultiMap<String, String> queryParams ) {
        Match match = router.getMatch( path, queryParams );
        if (match == null) {
            // No match in the table at the moment, but check the persistence
            // manager to see if it can restore an API spec which would enable this endpoint
            // System.err.println( ">> ----------------- " + SpecManagerFactory.get() );
            SpecManagerFactory.get().loadSpecFor(path);
            match = router.getMatch( path, queryParams );
        }
        return match;
    }
    
    @GET @Produces
    	( { 
    		"text/javascript"
    		, "application/javascript"
    		, "application/rdf+xml"
    		, "application/json"
    		, "application/xml"
    		, "text/turtle"
    		, "text/html"
    		, "text/xml" 
    		, "text/plain"
    	} )
    public Response requestHandler(
            @PathParam("path") String pathstub,
            @Context HttpHeaders headers, 
            @Context ServletContext servCon,
            @Context UriInfo ui) throws IOException, URISyntaxException 
    {
    	MultivaluedMap<String, String> rh = headers.getRequestHeaders();
    	String contextPath = servCon.getContextPath(); 
    	MultiMap<String, String> queryParams = JerseyUtils.convert(ui.getQueryParameters());
    	boolean dontCache = has( rh, "pragma", "no-cache" ) || has( rh, "cache-control", "no-cache" );
        Couple<String, String> pathAndType = parse( pathstub );
        Match matchAll = getMatch( "/" + pathstub, queryParams );
        Match matchTrimmed = getMatch( "/" + pathAndType.a, queryParams );
        Match match = matchTrimmed == null || notFormat( matchTrimmed, pathAndType.b ) ? matchAll : matchTrimmed;
        String formatSuffix = match == matchAll ? null : pathAndType.b;
        if (match == null) {
        	StatsValues.endpointNoMatch();
        	String item = router.findItemURIPath( "_", ui.getRequestUri(), "/" + pathstub );
        	if (item == null) 
        		return noMatchFound( pathstub, ui, pathAndType );
        	else 
        		return standardHeaders( Response.seeOther( new URI( item ) ) ).build();
        } else {
        	Times t = new Times( pathstub );
        	Controls c = new Controls( !dontCache, t );
        	int encodingHash = hashOf( headers.getRequestHeaders().get("Accept-Encoding") );
        	int mediaHash = hashOf( headers.getAcceptableMediaTypes() );
			int runHash = mediaHash + encodingHash;
            List<MediaType> mediaTypes = JerseyUtils.getAcceptableMediaTypes( headers );
            Response answer = runEndpoint( c, contextPath, runHash, servCon, ui, queryParams, mediaTypes, formatSuffix, match );
            t.done();
            return answer;
        }
    }
    
    private int hashOf( Object x ) {
		return x == null ? 0x12345678 : x.hashCode();
	}

	private boolean has( MultivaluedMap<String, String> rh, String key, String value ) {
    	List<String> values = rh.get( key );
		return values != null && values.contains( value );
	}

	protected static final boolean showMightHaveMeant = false;

	private Response noMatchFound( String pathstub, UriInfo ui, Couple<String, String> pathAndType ) {
		String preamble = ui.getBaseUri().toString();
		String message = "Could not find anything matching " + ("/" + pathstub);
		if (pathAndType.b != null) message += " (perhaps '" + pathAndType.b + "' is an incorrect format name?)";
	//
		if (showMightHaveMeant) {
			message += "<div style='margin-bottom: 2px'>you might have meant any of:</div>\n";
			List<String> templates = router.templates();
			Collections.reverse( templates );
			for (String template: templates) {
				message += "\n<div style='margin-left: 2ex'>" + maybeLink(preamble, template) + "</div>";
			}
		}
		return returnNotFound( message + "\n", "/" + pathstub );
	}
    
    private String maybeLink( String preamble, String template ) {
    	if (template.contains( "{")) return template;
    	return "<a href='" + preamble + template.substring(1) + "'>" + template + "</a>";
    }

	/**
        Answer true of m's endpoint has no formatter called type.
    */
    private boolean notFormat( Match m, String type ) {
    	return m.getEndpoint().getRendererNamed( type ) == null;
	}
    
	//** return (revised path, renderer name or null)
    // TODO work out a spec-conformant method for this lookup
    public static Couple<String, String> parse( String pathstub ) 
        {
        String path = pathstub, type = null;
        int dot = pathstub.lastIndexOf( '.' ) + 1;
        int slash = pathstub.lastIndexOf( '/' );
        if (dot > 0 && dot > slash) 
            { path = pathstub.substring(0, dot - 1); type = pathstub.substring(dot); }        
        return new Couple<String, String>( path, type );
        }

    private Response runEndpoint
    	( Controls c
    	, String contextPath
    	, int runHash
    	, ServletContext servCon
    	, UriInfo ui
    	, MultiMap<String, String> queryParams
    	, List<MediaType> mediaTypes
    	, String formatSuffix
    	, Match match
    	) {
    	URLforResource as = pathAsURLFactory(servCon);
    	URI requestUri = ui.getRequestUri();
    	log.info( "handling request " + requestUri );
    //
        try {
        	URI ru = makeRequestURI(ui, match, requestUri);
        	APIEndpoint ep = match.getEndpoint();
        	Renderer _default = APIEndpointUtil.getRenderer( ep, formatSuffix, mediaTypes );
        	        	
        	boolean needsVaryAccept = formatSuffix == null && queryParams.containsKey( "_format" ) == false;
        	if (formatSuffix == null && _default != null) formatSuffix = _default.getPreferredSuffix();
        	Triad<APIResultSet, String, Bindings> resultsAndFormat = APIEndpointUtil.call( c, match, ru, formatSuffix, contextPath, queryParams );
            APIResultSet results = resultsAndFormat.a;
            
			Bindings rc = new Bindings( resultsAndFormat.c.copy(), as );
			String _format = resultsAndFormat.b;
			String formatter = (_format.equals( "" ) ? formatSuffix : _format);
			
			Renderer r = APIEndpointUtil.getRenderer( ep, formatter, mediaTypes );
        	
        	if (_default.getPreferredSuffix().equals( r.getPreferredSuffix())) {
        		MediaType dmt = _default.getMediaType(rc);
        		if (!dmt.equals(r.getMediaType(rc))) {
        			r = RouterRestletSupport.changeMediaType( r, dmt );
        		}
        	}
			
			int mainHash = runHash + ru.toString().hashCode();
			return doRendering( c, rc, mainHash, needsVaryAccept, formatter, results, r );
        } catch (StackOverflowError e) {
        	StatsValues.endpointException();
            log.error("Stack Overflow Error" );
            if (log.isDebugEnabled()) log.debug( Messages.shortStackTrace( e ) );
            return standardHeaders( Response.serverError() ).entity( e.getMessage() ).build();
        } catch (UnknownShortnameException e) {
        	log.error( "UnknownShortnameException: " + e.getMessage() );
            if (log.isDebugEnabled()) log.debug( Messages.shortStackTrace( e ) );
        	StatsValues.endpointException();
        	return buildErrorResponse(e);
        } catch (EldaException e) {
        	StatsValues.endpointException();
        	log.error( "Exception: " + e.getMessage() );
        	if (log.isDebugEnabled())log.debug( Messages.shortStackTrace( e ) );
        	return buildErrorResponse(e);
        } catch (QueryParseException e) {
        	StatsValues.endpointException();
            log.error( "Query Parse Exception: " + e.getMessage() );
            if (log.isDebugEnabled())log.debug( Messages.shortStackTrace( e ) );
            return returnNotFound("Failed to parse query request : " + e.getMessage());
        } catch (Throwable e) {
        	log.error( "General failure: " + e.getMessage() );
        	e.printStackTrace( System.out );
        	StatsValues.endpointException();
            return returnError( e );
        }
    }    
    
    public static URI makeRequestURI(UriInfo ui, Match match, URI requestUri) {
		String base = match.getEndpoint().getSpec().getAPISpec().getBase();
		if (base == null) return requestUri;
		return URIUtils.resolveAgainstBase( requestUri, URIUtils.newURI( base ), ui.getPath() );
	}

	private static URLforResource pathAsURLFactory( final ServletContext servCon ) {
		return new URLforResource() 
			{
			@Override public URL asResourceURL( String ePath ) { 		
			String p = ePath.startsWith( "/" ) || ePath.startsWith( "http://") ? ePath : "/" + ePath;
			try {
				URL result = p.startsWith( "http:" ) ? new URL(p) : servCon.getResource( p );
				if (result == null) EldaException.NotFound( "webapp resource", ePath );
				return result;
				}
			catch (MalformedURLException e) 
				{
				throw new WrappedException( e );
				} 
			}
		};
	}
	
    private Response doRendering( Controls c, Bindings rc, int mainHash, boolean needsVaryAccept, String rName, APIResultSet rs, Renderer r ) {
		if (r == null) {
            String message = rName == null
            	? "no suitable media type was provided for rendering."
            	: "renderer '" + rName + "' is not known to this server."
            	;
            return standardHeaders( Response.status( Status.BAD_REQUEST ).entity( Messages.niceMessage( message ) ) ).build();
        } else {
			MediaType mt = r.getMediaType(rc);
			log.info( "rendering with formatter " + mt );
        	Times times = c.times;
			Renderer.BytesOut bo = r.render( times, rc, rs );
			return returnAs( rs, mainHash + mt.hashCode(), wrap(times, bo), needsVaryAccept, mt );
        }
	}

    public static ResponseBuilder standardHeaders( ResponseBuilder rb ) {
        return standardHeaders( null, 0, false, rb );
    }

    public static ResponseBuilder standardHeaders( APIResultSet rs, int envHash, ResponseBuilder rb ) {
        return standardHeaders( rs, envHash, false, rb );
    }

    public static ResponseBuilder standardHeaders( APIResultSet rs, int envHash, boolean needsVaryAccept, ResponseBuilder rb ) {
    	// rs may be null (no resultset for this header build)
    	rb = rb.header( ACCESS_CONTROL_ALLOW_ORIGIN, "*" );
        if (needsVaryAccept) rb = rb.header( VARY, "Accept" );
        if (rs != null && rs.enableETags()) rb = rb.tag( Long.toHexString( etagFor(rs, envHash) ) ); 
   		return rb;
    }

	private static long etagFor(APIResultSet rs, int envHash) {
		return rs.getHash() ^ envHash;
	}
    
    public static Response returnAs( String response, String mimetype) {
        return standardHeaders( Response.ok(response, mimetype) ).build();
    }
    
    private static Response returnAs( APIResultSet rs, int envHash, StreamingOutput response, boolean varyAccept, MediaType mt ) {
        try {
            return standardHeaders( rs, envHash, varyAccept, Response.ok( response, mt.toFullString() ) )
            	.contentLocation( rs.getContentLocation() )
            	.build()
            	;
        } catch (RuntimeException e) {
            return returnError(e);
        }
    }

    private static StreamingOutput wrap( final Times t, final BytesOut response ) {
		return new StreamingOutput() {
			
			@Override public void write(OutputStream os) throws IOException, WebApplicationException {
				response.writeAll(t, os);
				StatsValues.accumulate( t );
			}
		};
	}

	public static Response returnError( Throwable e ) {
        String shortMessage = e.getMessage();
		String longMessage = Messages.niceMessage( shortMessage, "Internal Server error." );
		log.error("Exception: " + shortMessage );
        log.debug( Messages.shortStackTrace( e ) );
        return standardHeaders( Response.serverError() ).entity( longMessage ).build();
    }

	public static Response returnError( String s ) {
        log.error("Exception: " + s );
        return standardHeaders( Response.serverError() ).entity( s ).build();
    }

    public static Response returnNotFound( String message ) {
    	return returnNotFound( message, "" );
    }
    
    public static Response returnNotFound( String message, String what ) {
        log.debug( "Failed to return results: " + Messages.brief( message ) );
        if (true) throw new NotFoundException();
        return standardHeaders( Response.status(Status.NOT_FOUND) ).entity( Messages.niceMessage( message, "404 Resource Not Found: " + what ) ).build();
    }
    
	private Response buildErrorResponse( EldaException e ) {
		return standardHeaders( Response.status( e.code ) )
			.entity( Messages.niceMessage( e ) )
			.build()
			;
	}
}
