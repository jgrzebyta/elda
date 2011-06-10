/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.rdfq;

import com.epimorphics.lda.support.PrefixLogger;

public interface RenderExpression
	{
	public StringBuilder render( PrefixLogger pl, StringBuilder out );
	public StringBuilder renderWrapped( PrefixLogger pl, StringBuilder out );
	}