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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

/**
 *
 */
public class ChannelBufferToInputStream extends AbstractDiscoverableTransformer
{

    public ChannelBufferToInputStream()
    {
        // if adding more types here, update doTransform() logic
        registerSourceType(DataTypeFactory.create(ChannelBuffer.class));
    }

    @Override
    protected InputStream doTransform(Object src, String enc) throws TransformerException
    {
        ChannelBuffer buffer = (ChannelBuffer) src;
        return new ChannelBufferInputStream(buffer);
    }
}
