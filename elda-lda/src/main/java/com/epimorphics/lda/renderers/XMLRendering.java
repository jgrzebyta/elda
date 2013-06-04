// Pending cleanup after soak tests and release. Now we have some regression
// tests, cleanup should be a safer activity.

///*
//    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
//    for the licence for this software.
//    
//    (c) Copyright 2011 Epimorphics Limited
//*/
//package com.epimorphics.lda.renderers;
//
//import java.util.*;
//
//import org.w3c.dom.Document;
//import org.w3c.dom.Element;
//
//import com.hp.hpl.jena.rdf.model.*;
//import com.hp.hpl.jena.sparql.vocabulary.FOAF;
//import com.hp.hpl.jena.vocabulary.RDF;
//import com.epimorphics.jsonrdf.ContextPropertyInfo;
//import com.epimorphics.jsonrdf.RDFUtil;
//import com.epimorphics.lda.core.APIResultSet.MergedModels;
//import com.epimorphics.lda.shortnames.ShortnameService;
//import com.epimorphics.lda.support.CycleFinder;
//import com.epimorphics.lda.support.MultiMap;
//import com.epimorphics.vocabs.API;
//
///**
//From the spec: 
//
//The XML formatter creates an XML representation that is very similar to the 
//JSON representation. The outermost object is a <result> element with 
//format and version attributes.
//
//The resource described in the <result> element is the entry point 
//into the graph, as described above (the item for an item endpoint, 
//the page for a list endpoint).
//
//Resources are mapped onto XML elements as follows:
//
//    * if the resource is a blank node that is the object or more 
//    	than one statement within the graph, the element is given a id 
//    	attribute that contains a unique identifier for that blank node
//    	
//    * otherwise, if the resource is not a blank node, 
//    	the element is given an href attribute that contains the 
//    	URI of the resource 
//
//The RDF properties of a resource are mapped onto XML elements. The 
//name of the XML element is:
//
//    * the short name for the property, as described in the property paths section, 
//      if it has one
//    
//    * the rdfs:label of the property, if it is a legal short name for a property 
//    	that doesn't clash with an existing name
//     
//    * the local name of the property (the part after the last hash or slash), 
//    	if it is a legal short name for a property that doesn't clash with an existing name
//     
//    * the prefix associated with the namespace of the property (the part
//     	before the last hash or slash), concatenated with an underscore, 
//     	concatenated with the local name of the property 
//
//The contents of the XML element is a sequence of <item> elements if the RDF 
//property has more than one value in the RDF graph or if the api:multiValued 
//property of the RDF property has the value true.
//
//Each RDF value is mapped onto some XML content as follows:
//
//    * if the value is a literal, it is mapped to a text node holding the 
//    	value itself; lang or datatype attributes on the element hold the
//    	language code and the short name of the datatype as applicable
//    
//    * otherwise, if the value is a rdf:List, it is mapped to a sequence 
//      of <item> elements, one representing each of the results of mapping 
//      the members of the list to XML
//    
//    * otherwise, if the value is a resource which is the subject of 
//    	a statement in the RDF graph, it is mapped onto an XML element
//    	as described here
//    
//    * otherwise, if the value is a blank node with no properties 
//    	it is mapped onto an empty XML element (with an id attribute
//    	if it it referenced more than once)
//    
//    * otherwise, if the value is a resource the element is given an
//    	href attribute whose value is the URI of the resource 
//
//
//*/
//public class XMLRendering {
//	
//	private final Document d;
//	private final ShortnameService sns;
//	private final Map<String, String> nameMap;
//	private final boolean suppressIPTO;
//	
//	private Resource itemsResource = null;
//	
//	private final Set<Resource> dontExpand = new HashSet<Resource>();
//	private final Set<Resource> cyclicOrSelected = new HashSet<Resource>();
//	
//	/** if true, property values will appear in sorted order */
//	private final boolean sortPropertyValues = true;
//	
//	public XMLRendering( Model m, ShortnameService sns, boolean suppressIPTO, Document d ) {
//		this.d = d;
//		this.sns = sns;
//		this.suppressIPTO = suppressIPTO;
//		this.nameMap = sns.nameMap().stage2().loadPredicates(m, m).result();
//	}
//
//	/** 
//		The way in -- all external uses come via this method, so we can
//		compute useful things before doing the actual rendering.
//		
//		<p>
//		We identify the items resource, ie the list of selected items,
//		and all the resources in it, as well as all the resources that
//		are part of a cycle in the graph. The selected items are treated
//		as having been "seen", ie they are not expanded, except at their
//		appearance in the items list. Otherwise cyclic items are expanded
//		on their first encounter in a top-down left-to-right-sorted walk
//		of the graph,
//		</p>
//	 */
//	public Element addResourceToElement( Element e, Resource x, MergedModels ignored ) {
//		System.err.println( ">> HERE WE ARE" );
//		x = x.inModel(ignored.getMergedModel() );
//		itemsResource = getItemsResource( x );
//		Set<RDFNode> selectedItems = getItemsList( itemsResource );
//		for (RDFNode m: selectedItems) dontExpand.add( m.asResource() );
//		cyclicOrSelected.addAll( CycleFinder.findCycles( x ) );
//		cyclicOrSelected.addAll( dontExpand );
//		return elementAddResource( e, x, false );
//	}
//
//	// Answer a List of all the items hanging off the RDF list
//	// itemsResource, or an empty list if itemsResource is null.
//	private Set<RDFNode> getItemsList( Resource itemsResource ) {
//		return itemsResource == null 
//			? new HashSet<RDFNode>() 
//			: new HashSet<RDFNode>( itemsResource.as( RDFList.class ).asJavaList() )
//			;
//	}
//	
//	// Answer the resource I that is the value of an api:items property
//	// of x, if there is one, and null otherwise. If x is the root
//	// of a resultset graph, I is the list of selected items.
//	private Resource getItemsResource( Resource x ) {
//		StmtIterator sit = x.listProperties( API.items );
//		return sit.hasNext() ? sit.next().getResource() : null;
//	}
//
//	static boolean newWay = true;
//	
//	/**
//	    Add a resource <code>x</code> to the DOM element <code>e</code>.
//	    x's properties are added to e if:
//	    
//	    <ul>
//	    	<li>
//	    		x is not involved in a cycle, and x is not a selected item.
//	    	</li>
//	    	<li>
//	    		x is cyclic (or a selected item) which hasn't already been
//	    		encountered and marked as dontExpand (which selected items are).
//	    	</li>
//	    	<li>
//	    		We've been told to expand it regardless, because this is a selected
//	    		item appearing in the selected-items list.
//	    	</li>
//	    </ul>
//	*/
//	private Element elementAddResource( Element e, Resource x, boolean expandRegardless ) {
//		addIdentification( e, x );
//		if (newWay) {
//			if (!cyclicOrSelected.contains( x ) || dontExpand.add( x ) || expandRegardless) {
//				List<Property> properties = asSortedList( x.listProperties().mapWith( Statement.Util.getPredicate ).toSet() );
//				if (suppressIPTO) properties.remove( FOAF.isPrimaryTopicOf );
//				for (Property p: properties) addPropertyValues( e, x, p, false );		
//			}
//		} else {
//			if (dontExpand.add( x )) {
//				List<Property> properties = asSortedList( x.listProperties().mapWith( Statement.Util.getPredicate ).toSet() );
//				if (suppressIPTO) properties.remove( FOAF.isPrimaryTopicOf );
//				for (Property p: properties) addPropertyValues( e, x, p, false );
//				dontExpand.remove( x );
//			}
//		}
//		return e;
//	}
//
//	private void addIdentification( Element e, Resource x ) {
//		if (x.isURIResource())  
//			e.setAttribute( "href", x.getURI() );
//		else if (dontExpand.contains( x )) {
//			e.setAttribute( "ref", idFor( e, x ) );
//		} else {
//			e.setAttribute( "id", idFor( e, x ) );
//		}
//	}
//
//	/**
//	    Attach a value to a property element.
//	*/
//	private Element giveValueToElement( Element pe, RDFNode v, boolean expandRegardless ) {
//		if (v.isLiteral()) {
//			addLiteralToElement( pe, (Literal) v );
//		} else {
//			Resource r = v.asResource();
//			if (inPlace( r )) {
//				addIdentification( pe, r );
//				if (expandRegardless) elementAddResource( pe, r, expandRegardless );
//			} else if (RDFUtil.isList( r )) {
//				for (RDFNode item: r.as(RDFList.class).asJavaList()) 
//					appendValueAsItem( pe, item, r.equals(itemsResource) );
//			} else if (r.listProperties().hasNext()) 
//				elementAddResource( pe, r, expandRegardless );
//			else if (v.isAnon()) {
//				if (needsId( v )) pe.setAttribute( "id", idFor( pe, r ) );
//			} else {
//				pe.setAttribute( "href", r.getURI() );
//			}
//		}
//		return pe;
//	}
//
//	private List<Property> asSortedList( Set<Property> set ) {
//		List<Property> properties = new ArrayList<Property>( set );
//		Collections.sort( properties, new Comparator<Property>() {
//            @Override public int compare(Property a, Property b) {
//                return nameMap.get( a.getURI() ).compareTo( nameMap.get( b.getURI() ) );
//            }
//        	} );
//		return properties;
//	}
//	
//	private List<RDFNode> sortObjects( Set<RDFNode> objects ) {
//		List<RDFNode> result = new ArrayList<RDFNode>( objects );
//		if (sortPropertyValues)
//			Collections.sort( result, new Comparator<RDFNode>() {
//	            @Override public int compare( RDFNode a, RDFNode b ) {
//	                return spelling( a ).compareTo( spelling( b ) );
//	            }
//	        	} );
//		return result;
//	}
//
//	protected String spelling( RDFNode n ) {
//		if (n.isURIResource()) return resourceSpelling( (Resource) n );
//		if (n.isLiteral()) return ((Literal) n).getLexicalForm();
//		return ((Resource) n).getId().toString();
//	}
//
//	private String resourceSpelling( Resource r ) {
//		String shorter = nameMap.get( r.getURI() );
//		return shorter == null ? r.getLocalName() : shorter;
//	}
//
//	private void addPropertyValues( Element e, Resource x, Property p, boolean expandRegardless ) {
//		// System.err.println( ">> add property values for " + p );
//		Element pe = d.createElement( shortNameFor( p ) );
//		// System.err.println( ">> pe := " + pe );
//		e.appendChild( pe );
//		// System.err.println( ">> e := " + e );
//		Set<RDFNode> values = x.listProperties( p ).mapWith( Statement.Util.getObject ).toSet();
//		if (values.size() > 1 || isMultiValued( p )) {
//			for (RDFNode value: sortObjects( values )) appendValueAsItem(pe, value, expandRegardless);
//		} else if (values.size() == 1) {
//			giveValueToElement( pe, values.iterator().next(), expandRegardless );
//		}
//	}
//
//	private void appendValueAsItem( Element pe, RDFNode value, boolean expandRegardless ) {
//		Element item = d.createElement( "item" );
//		giveValueToElement( item, value, expandRegardless );
//		pe.appendChild( item );
//	}
//
//	private boolean inPlace( Resource r ) {
//		if (r.isAnon()) return false;
//		if (dontExpand.contains( r )) return true;
//		if (r.listProperties().hasNext()) return false;
//		return true;
//	}
//
//	private void addLiteralToElement( Element e, Literal L ) {
//		String lang = L.getLanguage();
//		if (lang.length() > 0) e.setAttribute( "lang", lang );
//		String type = L.getDatatypeURI();
//		if (type != null) e.setAttribute( "datatype", shortNameFor( type ) );
//		e.appendChild( d.createTextNode( L.getLexicalForm() ) );
//	}
//
//	private boolean needsId( RDFNode v ) {
//		return false;
//	}
//
//	private boolean isMultiValued( Property p ) {
//		if (p.equals( RDF.type )) return true;
//		ContextPropertyInfo px = sns.asContext().getPropertyByURI(p.getURI());
//		return px != null && px.isMultivalued();
//	}
//	
//	private String shortNameFor( Resource r ) {
//		return shortNameFor( r.getURI() );
//	}
//	
//	private String shortNameFor( String URI ) {
//		return nameMap.get( URI );
//	}
//
//	final Map<AnonId, String> idMap = new HashMap<AnonId, String>();
//
//	private String idFor( Element e, Resource x ) {
//		String id = idMap.get(x.getId());
//		if (id == null) idMap.put(x.getId(), id = newId(e.getTagName()) );
//		return id;
//	}
//
//	int idCount = 1000;
//	
//	private String newId(String name) {
//		return "_:" + name + "-" + ++idCount;
//	}
//
//}




/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
*/
package com.epimorphics.lda.renderers;

import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.epimorphics.jsonrdf.ContextPropertyInfo;
import com.epimorphics.jsonrdf.RDFUtil;
import com.epimorphics.lda.core.APIResultSet.MergedModels;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.support.CycleFinder;
import com.epimorphics.util.Couple;
import com.epimorphics.vocabs.API;

/**
From the spec: 

The XML formatter creates an XML representation that is very similar to the 
JSON representation. The outermost object is a <result> element with 
format and version attributes.

The resource described in the <result> element is the entry point 
into the graph, as described above (the item for an item endpoint, 
the page for a list endpoint).

Resources are mapped onto XML elements as follows:

    * if the resource is a blank node that is the object or more 
    	than one statement within the graph, the element is given a id 
    	attribute that contains a unique identifier for that blank node
    	
    * otherwise, if the resource is not a blank node, 
    	the element is given an href attribute that contains the 
    	URI of the resource 

The RDF properties of a resource are mapped onto XML elements. The 
name of the XML element is:

    * the short name for the property, as described in the property paths section, 
      if it has one
    
    * the rdfs:label of the property, if it is a legal short name for a property 
    	that doesn't clash with an existing name
     
    * the local name of the property (the part after the last hash or slash), 
    	if it is a legal short name for a property that doesn't clash with an existing name
     
    * the prefix associated with the namespace of the property (the part
     	before the last hash or slash), concatenated with an underscore, 
     	concatenated with the local name of the property, if the local
     	name is allowable as a shortname;
     	
     * [Elda special] if that name isn't allowed as an element name, then
     	a reversible and almost readable encoding of the URI which is.

The contents of the XML element is a sequence of <item> elements if the RDF 
property has more than one value in the RDF graph or if the api:multiValued 
property of the RDF property has the value true.

Each RDF value is mapped onto some XML content as follows:

    * if the value is a literal, it is mapped to a text node holding the 
    	value itself; lang or datatype attributes on the element hold the
    	language code and the short name of the datatype as applicable
    
    * otherwise, if the value is a rdf:List, it is mapped to a sequence 
      of <item> elements, one representing each of the results of mapping 
      the members of the list to XML
    
    * otherwise, if the value is a resource which is the subject of 
    	a statement in the RDF graph, it is mapped onto an XML element
    	as described here
    
    * otherwise, if the value is a blank node with no properties 
    	it is mapped onto an empty XML element (with an id attribute
    	if it it referenced more than once)
    
    * otherwise, if the value is a resource the element is given an
    	href attribute whose value is the URI of the resource 


*/
public class XMLRendering {
	
	private final Document d;
	private final ShortnameService sns;
	private final Map<String, String> nameMap;
	private final boolean suppressIPTO;
	
	public XMLRendering( Model m, ShortnameService sns, boolean suppressIPTO, Document d ) {
		this.d = d;
		this.sns = sns;
		this.suppressIPTO = suppressIPTO;
		this.nameMap = sns.constructURItoShortnameMap(m, m);
	}

	/** 
		External rendering API. e is the XML element representing the result,
		x is the root node, and mm comprises the object and meta data.
	*/
	public Element addResourceToElement( Element e, Resource x, MergedModels mm ) {
		Resource xInMetaModel = x.inModel( mm.getMetaModel() );
		renderMetadata( e, x, xInMetaModel );
		renderObjectData( e, x, mm.getObjectModel(), xInMetaModel );
		return e;
	}

	public void renderMetadata(Element e, Resource x, Resource xInMetaModel) {
		Set<Resource> cyclic = CycleFinder.findCycles( xInMetaModel );
		Set<Resource> blocked = new HashSet<Resource>();
		Set<Resource> seen = new HashSet<Resource>();
		Trail t = new Trail( cyclic, seen, blocked );
	//
		blocked.add( x );
		Statement emv = xInMetaModel.getProperty( API.extendedMetadataVersion );
		if (emv != null) blocked.add( emv.getResource() );		
	//
		addIdentification( t, e, x );
		List<Property> metaProperties = asSortedList( xInMetaModel.listProperties().mapWith( Statement.Util.getPredicate ).toSet() );
		// if (suppressIPTO) properties.remove( FOAF.isPrimaryTopicOf );
		for (Property p: metaProperties) addPropertyValues( t, e, xInMetaModel, p );
	}
	
	public void renderObjectData(Element e, Resource x, Model objectModel, Resource xInMetaModel) {
		Set<Resource> blocked = new HashSet<Resource>();

		Set<RDFNode> selectedItems = getItemsList( getItemsResource( x ) );
				
		Set<Resource> selectedObjectItems = new HashSet<Resource>();
		for (RDFNode item: selectedItems) 
			if (item.isResource()) selectedObjectItems.add( item.asResource().inModel(objectModel) );
		
		blocked.addAll( selectedObjectItems );
		
		Set<Resource> cyclic = new HashSet<Resource>();
		Trail t = new Trail( cyclic, new HashSet<Resource>(), blocked );
		boolean hasPrimaryTopic = xInMetaModel.hasProperty( FOAF.primaryTopic );
		if (hasPrimaryTopic) { 	
			Resource primaryTopic = xInMetaModel.getProperty( FOAF.primaryTopic ).getResource().inModel(objectModel);		
			blocked.add( primaryTopic );
			cyclic.addAll( CycleFinder.findCycles( primaryTopic ) );
			topLevelExpansion(objectModel, t, findByNodeName( e, "primaryTopic" ));			
		} else {			
			cyclic.addAll( CycleFinder.findCycles( selectedObjectItems ) );
			NodeList nl = findItems( e ).getChildNodes();
			for (int i = 0; i < nl.getLength(); i += 1) {
				topLevelExpansion(objectModel, t, (Element) nl.item(i));
			}
		}
	}

	private void topLevelExpansion(Model objectModel, Trail t, Element pt) {
		Resource anItem = objectModel.createResource( pt.getAttribute( "href" ) );
		expandProperties(t, pt, anItem);
	}

	private void expandProperties(Trail t, Element pt, Resource anItem) {
		List<Property> properties = asSortedList( anItem.listProperties().mapWith( Statement.Util.getPredicate ).toSet() );
		for (Property ip: properties) addPropertyValues( t, pt, anItem, ip );
	}
	
	private Element findItems( Element e ) {
		return findByNodeName( e, "items" );
	}
	
	private Element findByNodeName( Element e, String name ) {
		NodeList nl = e.getChildNodes();
		for (int i = 0; i < nl.getLength(); i += 1) {
			Node it = nl.item(i);
			if (it.getNodeName().equals( name ))
				return (Element) it;
		}
//		System.err.println( ">> could not find " + name );
		return null;
	}

	// Answer a List of all the items hanging off the RDF list
	// itemsResource, or an empty list if itemsResource is null.
	private Set<RDFNode> getItemsList( Resource itemsResource ) {
		return itemsResource == null 
			? new HashSet<RDFNode>() 
			: new HashSet<RDFNode>( RDFUtil.asJavaList( itemsResource ) )
			;
	}

	// Answer the resource I that is the value of an api:items property
	// of x, if there is one, and null otherwise. If x is the root
	// of a resultset graph, I is the list of selected items.
	private Resource getItemsResource( Resource x ) {
		StmtIterator sit = x.listProperties( API.items );
		return sit.hasNext() ? sit.next().getResource() : null;
	}
	
	static class Trail {
		
		final Set<Resource> cyclic;
		final Set<Resource> blocked;
		final Set<Resource> seen;
		
		Trail( Set<Resource> cyclic, Set<Resource> seen, Set<Resource> blocked ) {
			this.cyclic = cyclic;
			this.blocked = blocked;
			this.seen = seen;
		}
		
		void markSeen( Resource x ) {
			seen.add( x );
		}
		
		boolean hasSeen( Resource x ) {
			return seen.contains( x );
		}
		
		boolean expand( Resource x ) {			
			if (blocked.contains( x )) return false;
			if (cyclic.contains( x )) return !seen.contains( x );
			return true;
		}
	}
	
	/**
	    Add a resource <code>x</code> to the DOM element <code>e</code>.
	*/	
	private Element elementAddResource( Trail t, Element e, Resource x ) {
		addIdentification( t, e, x );

		if (t.expand( x )) {
			t.markSeen( x );
			expandProperties(t, e, x);		
		}		
		
		return e;
	}

	private void addIdentification( Trail t, Element e, Resource x ) {
		if (x.isURIResource())  
			e.setAttribute( "href", x.getURI() );
		else if (t.hasSeen( x )) {
			e.setAttribute( "ref", idFor( e, x ) );
		} else {
			e.setAttribute( "id", idFor( e, x ) );
		}
	}

	/**
	    Attach a value to a property element.
	*/
	private Element giveValueToElement( Trail t, Element pe, RDFNode v ) {
		if (v.isLiteral()) {
			addLiteralToElement( pe, (Literal) v );
		} else {
			Resource r = v.asResource();
			if (inPlace( t, r )) {
				addIdentification( t, pe, r );
				elementAddResource( t, pe, r );
			} else if (RDFUtil.isList( r )) {
				for (RDFNode item: RDFUtil.asJavaList( r ) ) {
					appendValueAsItem( t, pe, item );
				}
			} else if (r.listProperties().hasNext()) 
				elementAddResource( t, pe, r );
			else if (v.isAnon()) {
				if (needsId( v )) pe.setAttribute( "id", idFor( pe, r ) );
			} else {
				pe.setAttribute( "href", r.getURI() );
			}
		}
		return pe;
	}

	// true if r is a named resource which has been expanded or has no properties
	private boolean inPlace( Trail t, Resource r ) {
		if (r.isAnon()) return false;
		if (t.hasSeen( r )) return true;
		if (r.listProperties().hasNext()) return false;
		return true;
	}

	private List<Property> asSortedList( Set<Property> set ) {
		List<Property> properties = new ArrayList<Property>( set );
		Collections.sort( properties, new Comparator<Property>() {
            @Override public int compare(Property a, Property b) {
                return nameMap.get( a.getURI() ).compareTo( nameMap.get( b.getURI() ) );
            }
        	} );
		return properties;
	}

	private static final Comparator<? super Couple<RDFNode, String>> compareCouples = new Comparator<Couple<RDFNode, String>>() {
		@Override public int compare( Couple<RDFNode, String> x, Couple<RDFNode, String> y) {
			return x.b.compareTo( y.b );
		}
	} ;
	
	private List<RDFNode> sortObjects( Property predicate, Set<RDFNode> objects ) {
		List<Couple<RDFNode, String>> labelleds = new ArrayList<Couple<RDFNode, String>>();
		for (RDFNode r: objects) labelleds.add( new Couple<RDFNode, String>( r, labelOf( r ) ) ); 
		Collections.sort( labelleds, compareCouples );				
		List<RDFNode> result = new ArrayList<RDFNode>();
		for (Couple<RDFNode, String> labelled: labelleds) result.add( labelled.a );
		return result;
	}

	/**
	    For sorting values, every node should have a label. That's easy for literals
	    and URI nodes, but bnode IDs are not stable, and for testing purposes if
	    nothing else, we want the order of values to be the same every time. So
	    for a bnode we give it the spelling of the lexical form of its literal label, 
	    if it has one. We try api:label then rdfs:label and otherwise fall back to
	    the bnode ID and cross our fingers.
	*/
	private String labelOf( RDFNode r ) {
		if (r.isAnon()) {
			Statement labelling = r.asResource().getProperty( API.label );
			if (labelling == null) labelling = r.asResource().getProperty( RDFS.label );
			if (labelling != null) {
				RDFNode label = labelling.getObject();
				if (label.isLiteral()) return label.asLiteral().getLexicalForm();
			}
		}
		return spelling( r );
	}

	protected String spelling( RDFNode n ) {
		if (n.isURIResource()) return resourceSpelling( (Resource) n );
		if (n.isLiteral()) return ((Literal) n).getLexicalForm();
		String id = ((Resource) n).getId().toString();
		return id;
	}

	private String resourceSpelling( Resource r ) {
		String shorter = nameMap.get( r.getURI() );
		return shorter == null ? r.getLocalName() : shorter;
	}

	private void addPropertyValues( Trail t, Element e, Resource x, Property p ) {		
		Element pe = d.createElement( shortNameFor( p ) );
		e.appendChild( pe );
		Set<RDFNode> values = x.listProperties( p ).mapWith( Statement.Util.getObject ).toSet();		
	//
		if (values.size() > 1 || isMultiValued( p )) {
			for (RDFNode value: sortObjects( p, values )) {
				appendValueAsItem(t, pe, value);
			}
		} else if (values.size() == 1) {
			giveValueToElement( t, pe, values.iterator().next() );
		}
	}

	private void appendValueAsItem( Trail t, Element pe, RDFNode value ) {
		Element item = d.createElement( "item" );
		giveValueToElement( t, item, value );
		pe.appendChild( item );
	}

	private void addLiteralToElement( Element e, Literal L ) {
		String lang = L.getLanguage();
		if (lang.length() > 0) e.setAttribute( "lang", lang );
		String type = L.getDatatypeURI();
		if (type != null) e.setAttribute( "datatype", shortNameFor( type ) );
		e.appendChild( d.createTextNode( L.getLexicalForm() ) );
	}

	private boolean needsId( RDFNode v ) {
		return false;
	}

	private boolean isMultiValued( Property p ) {
		if (p.equals( RDF.type )) return true;
		ContextPropertyInfo px = sns.asContext().getPropertyByURI(p.getURI());
		return px != null && px.isMultivalued();
	}
	
	private String shortNameFor( Resource r ) {
		return shortNameFor( r.getURI() );
	}
	
	private String shortNameFor( String URI ) {
		String s = nameMap.get( URI );
		if (s == null) s = URI.replaceFirst( ".*[#/]",  "" );
		return s;
	}

	final Map<AnonId, String> idMap = new HashMap<AnonId, String>();

	private String idFor( Element e, Resource x ) {
		String id = idMap.get(x.getId());
		if (id == null) idMap.put(x.getId(), id = newId(e.getTagName()) );
		return id;
	}

	int idCount = 1000;
	
	private String newId(String name) {
		return "_:" + name + "-" + ++idCount;
	}

}