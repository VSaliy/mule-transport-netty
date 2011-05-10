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

import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.i18n.CoreMessages;
import org.mule.transport.AbstractMessageDispatcher;
import org.mule.transport.ConnectException;
import org.mule.util.ExceptionUtils;
import org.mule.util.concurrent.Latch;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Executors;

import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * <code>NettyMessageDispatcher</code> TODO document
 */
public class NettyMessageDispatcher extends AbstractMessageDispatcher
{
    protected ChannelGroup allChannels;

    protected Channel channel;
    protected ClientBootstrap bootstrap;

    /**
     * An exchanger used in async socket dispatches to provide request-reply behavior.
     */
    protected Exchanger<Object> exchanger = new Exchanger<Object>();

    /* For general guidelines on writing transports see
       http://www.mulesoft.org/documentation/display/MULE3USER/Creating+Transports */

    public NettyMessageDispatcher(OutboundEndpoint endpoint)
    {
        super(endpoint);
    }

    @Override
    protected void doInitialise() throws InitialisationException
    {
        super.doInitialise();
        allChannels = new DefaultChannelGroup(this.getDispatcherName() + ".all-channels");
    }

    @Override
    public void doConnect() throws Exception
    {
        URI uri = null;
        try
        {
            uri = endpoint.getEndpointURI().getUri();

            bootstrap = new ClientBootstrap(
                    new NioClientSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

            // Set up the pipeline factory.
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    return Channels.pipeline(
                            new StringEncoder(Charset.forName(endpoint.getEncoding())),
                            new NettyDispatcherUpstreamHandler()
                    );
                }
            });

            final ChannelFuture future = bootstrap.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
            final Latch connectLatch = new Latch();
            future.addListener(new ChannelFutureListener()
            {
                public void operationComplete(ChannelFuture future) throws Exception
                {
                    channel = future.getChannel();
                    connectLatch.release();
                }
            });

            connectLatch.await(endpoint.getResponseTimeout(), TimeUnit.MILLISECONDS);

            // TODO close all connections
            allChannels.add(channel);
        }
        catch (Exception e)
        {
            // TODO message
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            throw new ConnectException(CoreMessages.createStaticMessage("Failed to connect to uri " + uri),
                                       rootCause == null ? e : rootCause, this);
            //throw new ConnectException(NettyMessages.failedToBindToUri(uri), ExceptionUtils.getRootCause(e), this);
        }

    }

    @Override
    public void doDisconnect() throws Exception
    {
        allChannels.close().awaitUninterruptibly();
    }

    @Override
    public void doDispatch(MuleEvent event) throws Exception
    {
        /* IMPLEMENTATION NOTE: This is invoked when the endpoint is
           asynchronous.  It should invoke the transport but not return any
           result.  If a result is returned it should be ignorred, but if the
           underlying transport does have a notion of asynchronous processing,
           that should be invoked.  This method is executed in a different
           thread to the request thread. */


        /* IMPLEMENTATION NOTE: The event message needs to be transformed for the outbound transformers to take effect. This
           isn't done automatically in case the dispatcher needs to modify the message before apllying transformers.  To
           get the transformed outbound message call -
           event.transformMessage(); */

        // TODO Write the client code here to dispatch the event over this transport

        throw new UnsupportedOperationException("doDispatch");
    }

    @Override
    public MuleMessage doSend(MuleEvent event) throws Exception
    {
        MuleMessage response = null;
        if (channel.isConnected())
        {
            channel.write(event.getMessage().getPayloadAsString(endpoint.getEncoding()));
        }
        // TODO throw an error if not connected
        if (event.getEndpoint().getExchangePattern().hasResponse())
        {
            Object result = exchanger.exchange(event, event.getTimeout(), java.util.concurrent.TimeUnit.MILLISECONDS);
            response = muleMessageFactory.create(result, endpoint.getEncoding());

        }

        return response;
    }

    @Override
    public void doDispose()
    {
        if (bootstrap != null)
        {
            //bootstrap.releaseExternalResources();
        }
    }

    protected class NettyDispatcherUpstreamHandler extends SimpleChannelUpstreamHandler
    {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
        {
            super.messageReceived(ctx, e);
            final Object msg = e.getMessage();
            // TODO timeout
            exchanger.exchange(msg);
            System.out.println("Got response!");
        }
    }
}

