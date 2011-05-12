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

import org.mule.api.ExceptionPayload;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.util.ExceptionUtils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

/**
 * Server-side Mule-Netty integration point.
 */
public class MuleUpstreamHandler extends SimpleChannelUpstreamHandler
{

    private static final Logger logger = Logger.getLogger(MuleUpstreamHandler.class.getName());

    private final AtomicLong transferredBytes = new AtomicLong();
    private NettyMessageReceiver receiver;

    public MuleUpstreamHandler(NettyMessageReceiver receiver)
    {
        this.receiver = receiver;
    }

    public long getTransferredBytes()
    {
        return transferredBytes.get();
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent event)
    {
        // Send back the received message to the remote peer.
        final ChannelBuffer buffer = (ChannelBuffer) event.getMessage();
        transferredBytes.addAndGet(buffer.readableBytes());

        //final ChannelBuffer copy = ChannelBuffers.copiedBuffer(buffer);
        //final String s = copy.toString(Charset.forName(receiver.getEndpoint().getEncoding()));

        final Channel channel = event.getChannel();
        try
        {

            // TODO create a NettyMuleMessageFactory
            final MuleMessage muleMessage = receiver.createMuleMessage(buffer, receiver.getEndpoint().getEncoding());

            if (receiver.getEndpoint().getExchangePattern().hasResponse())
            {
                final MuleEvent result = receiver.routeMessage(muleMessage);

                final MuleMessage message = result.getMessage();
                final ExceptionPayload exceptionPayload = message.getExceptionPayload();
                if (exceptionPayload == null)
                {
                    if (message.getPayload() instanceof InputStream)
                    {
                        final ChunkedStream stream = new ChunkedStream(message.getPayload(InputStream.class));
                        channel.getPipeline().addLast("streamer", new ChunkedWriteHandler());
                        channel.write(stream).addListener(ChannelFutureListener.CLOSE);
                    }
                    else
                    {
                        // TODO transport transformers
                        final ChannelBuffer out = ChannelBuffers.wrappedBuffer(message.getPayloadAsBytes());
                        channel.write(out).addListener(ChannelFutureListener.CLOSE);
                    }
                }
                else
                {
                    // send an error message from the root exception
                    channel.getPipeline().addLast("encoder", new StringEncoder(Charset.forName(receiver.getEndpoint().getEncoding())));
                    final String rootCause = ExceptionUtils.getRootCauseMessage(exceptionPayload.getException());

                    // TODO check bytes encoding
                    channel.write(rootCause).addListener(ChannelFutureListener.CLOSE);
                }
            }
            else
            {
                channel.close();
                receiver.routeMessage(muleMessage);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            channel.close();
        }

    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e)
    {
        //receiver.getFlowConstruct().getExceptionListener().handleException(e)
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
}
