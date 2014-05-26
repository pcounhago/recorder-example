/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.uvigo.kurento.ims;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.kurento.kmf.media.MediaPipeline;
import com.kurento.kmf.media.MediaPipelineFactory;
import com.kurento.kmf.media.MediaProfileSpecType;
import com.kurento.kmf.media.RecorderEndpoint;
import com.kurento.kmf.media.RtpEndpoint;
import com.kurento.kmf.media.events.ErrorEvent;
import com.kurento.kmf.media.events.MediaEventListener;
import com.kurento.kmf.media.events.MediaSessionStartedEvent;
import com.kurento.kmf.media.events.MediaSessionTerminatedEvent;

/**
 * This example shows a typical UAS and reply 200 OK to any INVITE or BYE it receives
 * 
 * @author Jean Deruelle
 *
 */
public class RecorderSipServlet extends SipServlet {
	
	private static final long serialVersionUID = 1L;

	private static Log logger = LogFactory.getLog(RecorderSipServlet.class);
	
	public static final String TARGET = "file:///tmp/recording";
	
	@Autowired
	private MediaPipelineFactory mpf;
	private MediaPipeline mp;
	
	private RtpEndpoint rtpEndpoint;
	private RecorderEndpoint recorderEndPoint;
	
	/** Creates a new instance of RecorderSipServlet */
	public RecorderSipServlet() {
	    ApplicationContext context = new FileSystemXmlApplicationContext(new String[] {"file:/home/pcounhago/kmf-media-config.xml"});
	    mpf = context.getBean(MediaPipelineFactory.class);
	}
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		logger.info("the RecorderSipServlet servlet has been started");
		super.init(servletConfig);
	}

	@Override
	protected void doInvite(SipServletRequest request) throws ServletException,
			IOException {
		logger.info("Got request:\n"
				+ request.toString());
		String fromUri = request.getFrom().getURI().toString();
		logger.info(fromUri);
		
		mp = mpf.create();
		
		String remoteSDP = new String(request.getRawContent(),"UTF-8");
		logger.info("Remote SDP record:\n"+remoteSDP+"\n");
		

		// By default recording in WEBM format
		MediaProfileSpecType mediaProfileSpecType = MediaProfileSpecType.WEBM;

		recorderEndPoint = mp.newRecorderEndpoint(TARGET).withMediaProfile(mediaProfileSpecType).build();
		
		rtpEndpoint = mp.newRtpEndpoint().build();
		rtpEndpoint.connect(rtpEndpoint);
		rtpEndpoint.connect(recorderEndPoint);
		
		rtpEndpoint.addMediaSessionStartedListener(new MediaEventListener<MediaSessionStartedEvent>() {
			
			@Override
			public void onEvent(MediaSessionStartedEvent event) {
				logger.info("Session started!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n\n");				
			}
		});
		
		rtpEndpoint.addMediaSessionTerminatedListener(new MediaEventListener<MediaSessionTerminatedEvent>() {
			
			@Override
			public void onEvent(MediaSessionTerminatedEvent event) {
				logger.info("Session Terminated!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n\n");				
			}
		});
		
		rtpEndpoint.addErrorListener(new MediaEventListener<ErrorEvent>() {
			@Override
			public void onEvent(ErrorEvent event){
				logger.info("Session Error!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n\n");	
			}
		});
		
		String prevSDP = rtpEndpoint.generateOffer();
		logger.info("Server SDP offer:\n"+prevSDP+"\n");
		
		String localSDP = rtpEndpoint.processOffer(remoteSDP+"a=sendrecv");
		logger.info("Local SDP record:\n"+localSDP+"\n");
		
		recorderEndPoint.record();
		
		SipServletResponse trying = request.createResponse(SipServletResponse.SC_TRYING);
		trying.send();
		SipServletResponse ringing = request.createResponse(SipServletResponse.SC_RINGING);
		ringing.send();
		SipServletResponse ok = request.createResponse(SipServletResponse.SC_OK);
		ok.setContent(localSDP,"application/sdp");
		ok.send();		
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException,
			IOException {
		SipServletResponse sipServletResponse = request.createResponse(SipServletResponse.SC_OK);
		sipServletResponse.send();	
	}
}
