/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.netty;

import org.mule.api.transformer.DataType;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractTransformer;
import org.mule.transformer.types.SimpleDataType;

import java.io.UnsupportedEncodingException;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 *
 */
public class ChannelBufferToStringTransformer extends AbstractTransformer
{

    public ChannelBufferToStringTransformer()
    {
        registerSourceType(new SimpleDataType<ChannelBuffer>(ChannelBuffer.class));
    }

    @Override
    protected Object doTransform(Object src, String enc) throws TransformerException
    {
        try
        {
            final ChannelBuffer buffer = (ChannelBuffer) src;
            return new String(buffer.array(), enc);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new TransformerException(this, e);
        }
    }
}
