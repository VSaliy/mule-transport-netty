/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.netty.transformers;

import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractDiscoverableTransformer;
import org.mule.transformer.types.DataTypeFactory;

import java.io.InputStream;

import org.jboss.netty.handler.stream.ChunkedStream;

/**
 * A transformer to be paired with the {@link org.jboss.netty.handler.stream.ChunkedWriteHandler}
 *
 * @see org.jboss.netty.handler.stream.ChunkedWriteHandler
 */
public class InputStreamToChunkedStream extends AbstractDiscoverableTransformer
{

    public InputStreamToChunkedStream()
    {
        // if adding more types here, update doTransform() logic
        registerSourceType(DataTypeFactory.create(InputStream.class));
    }

    @Override
    protected ChunkedStream doTransform(Object src, String enc) throws TransformerException
    {
        return new ChunkedStream((InputStream) src);
    }
}
