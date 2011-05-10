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

import org.mule.api.MuleContext;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.lifecycle.CreateException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.Connector;
import org.mule.config.i18n.CoreMessages;
import org.mule.transport.AbstractMessageReceiver;
import org.mule.transport.ConnectException;
import org.mule.transport.netty.i18n.NettyMessages;
import org.mule.util.ExceptionUtils;
import org.mule.util.StringUtils;
import org.mule.util.concurrent.NamedThreadFactory;
import org.mule.util.concurrent.ThreadNameHelper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * <code>NettyMessageReceiver</code> TODO document
 */
public class NettyMessageReceiver extends  AbstractMessageReceiver
{
    protected final AtomicBoolean disposing = new AtomicBoolean(false);
    protected ServerBootstrap bootstrap;
    protected ChannelGroup allChannels;

    public NettyMessageReceiver(Connector connector, FlowConstruct flowConstruct,
                                InboundEndpoint endpoint)
            throws CreateException
    {
        super(connector, flowConstruct, endpoint);
    }

    @Override
    protected void doInitialise() throws InitialisationException
    {
        // Configure the server.
        allChannels = new DefaultChannelGroup(this.getReceiverKey() + ".all-channels");
        // Set up the pipeline factory.
        final MuleContext muleContext = connector.getMuleContext();
        final ExecutorService bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory(
                ThreadNameHelper.receiver(muleContext, connector.getName()) + ".boss",
                muleContext.getExecutionClassLoader()
        ));

        final NamedThreadFactory threadFactory = new NamedThreadFactory(
                ThreadNameHelper.receiver(muleContext, connector.getName()) + ".worker",
                muleContext.getExecutionClassLoader()
        );
        final ThreadingProfile tp = connector.getReceiverThreadingProfile();

        final ExecutorService workerExecutor = new ThreadPoolExecutor(32, 32, tp.getThreadTTL(), java.util.concurrent.TimeUnit.MILLISECONDS,
                                                                      new ArrayBlockingQueue<Runnable>(1000),
                                                                      threadFactory,
                                                                      new ThreadPoolExecutor.AbortPolicy()
        );


        this.bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        bossExecutor,
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
                p.addLast("handler-mule", new MuleServerUpstreamHandler(NettyMessageReceiver.this));

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
            throw new ConnectException(CoreMessages.createStaticMessage("Failed to bind to uri " + uri),
                                       ExceptionUtils.getRootCause(e), this);
            //throw new ConnectException(NettyMessages.failedToBindToUri(uri), ExceptionUtils.getRootCause(e), this);
        }
    }

    @Override
    public void doDisconnect() throws ConnectException
    {
        /* IMPLEMENTATION NOTE: Disconnects and tidies up any rources allocted
           using the doConnect() method. This method should return the
           MessageReceiver into a disconnected state so that it can be
           connected again using the doConnect() method. */

        // TODO release any resources here
    }

    @Override
    public void doStart()
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Should perform any actions necessary to enable
           the reciever to start reciving events. This is different to the
           doConnect() method which actually makes a connection to the
           transport, but leaves the MessageReceiver in a stopped state. For
           polling-based MessageReceivers the start() method simply starts the
           polling thread. What action is performed here depends on
           the transport being used. Most of the time a custom provider
           doesn't need to override this method. */
    }

    @Override
    public void doStop()
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Should perform any actions necessary to stop
           the reciever from receiving events. */
    }

    @Override
    public void doDispose()
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Is called when the Conector is being dispoed
           and should clean up any resources. The doStop() and doDisconnect()
           methods will be called implicitly when this method is called. */
    }
    
}
