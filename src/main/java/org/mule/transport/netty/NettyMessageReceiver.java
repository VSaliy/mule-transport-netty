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
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.lifecycle.CreateException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.Connector;
import org.mule.config.i18n.CoreMessages;
import org.mule.transport.AbstractMessageReceiver;
import org.mule.transport.ConnectException;
import org.mule.util.ExceptionUtils;
import org.mule.util.StringUtils;
import org.mule.util.concurrent.NamedThreadFactory;
import org.mule.util.concurrent.ThreadNameHelper;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

/**
 * <code>NettyMessageReceiver</code> TODO document
 */
public class NettyMessageReceiver extends  AbstractMessageReceiver
{
    protected final AtomicBoolean disposing = new AtomicBoolean(false);
    protected ServerBootstrap bootstrap;
    protected ChannelGroup allChannels;
    protected NettyConnector nettyConnector;

    public NettyMessageReceiver(Connector connector, FlowConstruct flowConstruct,
                                InboundEndpoint endpoint)
            throws CreateException
    {
        super(connector, flowConstruct, endpoint);
        this.nettyConnector = (NettyConnector) connector;
    }

    @Override
    protected void doInitialise() throws InitialisationException
    {
        // Configure the server.
        allChannels = new DefaultChannelGroup(this.getReceiverKey() + ".all-channels");
        // Set up the pipeline factory.
        final MuleContext muleContext = connector.getMuleContext();

        // TODO non-clashing receiver names, threading works differently for netty (back to Mule 1.x days!)

        final NamedThreadFactory threadFactory = new NamedThreadFactory(
                String.format("%s[%s].worker", ThreadNameHelper.receiver(muleContext, connector.getName()), getReceiverKey()),
                muleContext.getExecutionClassLoader()
        );
        final ThreadingProfile tp = connector.getReceiverThreadingProfile();

        // TODO configurable pool size
        final ExecutorService workerExecutor = new ThreadPoolExecutor(32, 32, tp.getThreadTTL(), java.util.concurrent.TimeUnit.MILLISECONDS,
                                                                      new ArrayBlockingQueue<Runnable>(1000),
                                                                      threadFactory,
                                                                      new ThreadPoolExecutor.AbortPolicy()
        );


        this.bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        nettyConnector.bossExecutor,
                        // TODO why not extract this value from the workExecutor pool?
                        workerExecutor));
        this.bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            public ChannelPipeline getPipeline() throws Exception
            {
                ChannelPipeline p = Channels.pipeline();
                //p.addLast("encoder-string", new StringEncoder(Charset.forName(endpoint.getEncoding())));
                //p.addLast("decoder-length", new LengthFieldBasedFrameDecoder(1049000, 0, 2, 0, 2));
                //p.addLast("encoder-length", new LengthFieldPrepender(2));
                //p.addLast("chunker", new FixedLengthFrameDecoder(16384));
                //p.addLast("executor", new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576)));
                p.addLast("handler-mule", new MuleReceiverUpstreamHandler());

                return p;
            }
        });

    }

    @Override
    public void doConnect() throws ConnectException
    {
        URI uri = null;
        try
        {
            disposing.set(false);

            uri = endpoint.getEndpointURI().getUri();

            String host = StringUtils.defaultIfEmpty(uri.getHost(), "localhost");
            // Bind and start to accept incoming connections.
            final Channel channel = bootstrap.bind(new InetSocketAddress(host, uri.getPort()));
            allChannels.add(channel);
        }
        catch (Exception e)
        {
            // TODO message
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            throw new ConnectException(CoreMessages.createStaticMessage("Failed to bind to uri " + uri),
                                       rootCause == null ? e : rootCause, this);
            //throw new ConnectException(NettyMessages.failedToBindToUri(uri), ExceptionUtils.getRootCause(e), this);
        }
    }

    @Override
    public void doDisconnect() throws ConnectException
    {
        disposing.set(true);
        allChannels.close().awaitUninterruptibly();
    }

    @Override
    public void doStart()
    {
    }

    @Override
    public void doStop()
    {
    }

    @Override
    public void doDispose()
    {
        if (bootstrap != null)
        {
            //bootstrap.releaseExternalResources();
        }
    }

    /**
     * Server-side Mule-Netty integration point.
     */
    public class MuleReceiverUpstreamHandler extends SimpleChannelUpstreamHandler
    {

        @Override
        public void messageReceived(
                ChannelHandlerContext ctx, MessageEvent event)
        {
            final ChannelBuffer buffer = (ChannelBuffer) event.getMessage();

            final Channel channel = event.getChannel();
            try
            {

                final NettyMessageReceiver receiver = NettyMessageReceiver.this;
                // TODO create a NettyMuleMessageFactory
                final MuleMessage muleMessage = receiver.createMuleMessage(buffer, receiver.getEndpoint().getEncoding());

                if (receiver.getEndpoint().getExchangePattern().hasResponse())
                {
                    final MuleEvent result = receiver.routeMessage(muleMessage);

                    final MuleMessage message = result.getMessage();
                    final ExceptionPayload exceptionPayload = message.getExceptionPayload();
                    if (exceptionPayload == null)
                    {
                        // happy path with no errors
                        final Object payload = message.getPayload();
                        if (payload instanceof InputStream)
                        {
                            final ChunkedStream stream = new ChunkedStream(message.getPayload(InputStream.class));
                            channel.getPipeline().addLast("streamer", new ChunkedWriteHandler());
                            channel.write(stream).addListener(ChannelFutureListener.CLOSE);
                        }
                        else if (payload instanceof ChannelBuffer)
                        {
                            // native ChannelBuffer format
                            channel.write(payload).addListener(ChannelFutureListener.CLOSE);
                        }
                        else
                        {
                            // otherwise dump bytes
                            final ChannelBuffer out = ChannelBuffers.wrappedBuffer(message.getPayloadAsBytes());
                            channel.write(out).addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                    else
                    {
                        // got an exception payload in the response
                        // send an error message from the root exception
                        channel.getPipeline().addLast("encoder", new StringEncoder(Charset.forName(receiver.getEndpoint().getEncoding())));
                        final String rootCause = ExceptionUtils.getRootCauseMessage(exceptionPayload.getException());

                        // TODO check bytes encoding
                        channel.write(rootCause).addListener(ChannelFutureListener.CLOSE);
                    }
                }
                else
                {
                    // async invocation, close the channel and proceed
                    channel.close();
                    receiver.routeMessage(muleMessage);
                }
            }
            catch (Exception e)
            {
                // TODO error handling
                e.printStackTrace();
                channel.close();
            }

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        {
            //receiver.getFlowConstruct().getExceptionListener().handleException(e)
            // TODO better error handling
            e.getCause().printStackTrace();
            e.getChannel().close();
        }
    }
}
