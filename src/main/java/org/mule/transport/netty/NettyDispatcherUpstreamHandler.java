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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class NettyDispatcherUpstreamHandler extends SimpleChannelUpstreamHandler
    {
        protected NettyMessageDispatcher dispatcher;

        public NettyDispatcherUpstreamHandler(NettyMessageDispatcher dispatcher)
        {
            this.dispatcher = dispatcher;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
        {
            super.messageReceived(ctx, e);
            final Object msg = e.getMessage();
            // short timeout, if there was noone waiting for response, it was an error
            dispatcher.exchanger.exchange(msg, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

    }
