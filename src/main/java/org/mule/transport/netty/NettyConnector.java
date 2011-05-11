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
import org.mule.api.MuleException;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.transport.AbstractConnector;
import org.mule.util.concurrent.NamedThreadFactory;
import org.mule.util.concurrent.ThreadNameHelper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

/**
 * <code>NettyConnector</code> TODO document
 */
public class NettyConnector extends AbstractConnector
{

    protected ExecutorService bossExecutor;
    protected ExecutorService dispatcherExecutor;
    protected NioClientSocketChannelFactory clientSocketChannelFactory;

    public NettyConnector(MuleContext context)
    {
        super(context);
    }

    @Override
    public void doInitialise() throws InitialisationException
    {
        bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory(
                String.format("%s%s.boss", ThreadNameHelper.getPrefix(muleContext), getName()),
                muleContext.getExecutionClassLoader()
        ));

        final NamedThreadFactory threadFactory = new NamedThreadFactory(
                String.format("%s.worker", ThreadNameHelper.dispatcher(muleContext, getName())),
                muleContext.getExecutionClassLoader()
        );
        final ThreadingProfile tp = getDispatcherThreadingProfile();

        // TODO configurable pool size
        dispatcherExecutor = new ThreadPoolExecutor(32, 32, tp.getThreadTTL(),
                                                    java.util.concurrent.TimeUnit.MILLISECONDS,
                                                    new ArrayBlockingQueue<Runnable>(1000),
                                                    threadFactory,
                                                    new ThreadPoolExecutor.AbortPolicy()
        );

        clientSocketChannelFactory = new NioClientSocketChannelFactory(bossExecutor, dispatcherExecutor);
    }

    @Override
    public void doConnect() throws Exception
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Makes a connection to the underlying
           resource. When connections are managed at the receiver/dispatcher
           level, this method may do nothing */
    }

    @Override
    public void doDisconnect() throws Exception
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Disconnects any connections made in the
           connect method If the connect method did not do anything then this
           method shouldn't do anything either. */
    }

    @Override
    public void doStart() throws MuleException
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: If there is a single server instance or
           connection associated with the connector i.e. Jms Connection or Jdbc Connection, 
           this method should put the resource in a started state here. */
    }

    @Override
    public void doStop() throws MuleException
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Should put any associated resources into a
           stopped state. Mule will automatically call the stop() method. */
    }

    @Override
    public void doDispose()
    {
        // Optional; does not need to be implemented. Delete if not required

        /* IMPLEMENTATION NOTE: Should clean up any open resources associated
           with the connector. */
    }

    public String getProtocol()
    {
        return "netty";
    }
}
