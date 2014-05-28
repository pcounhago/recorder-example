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

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.kurento.kmf.media.JackVaderFilter;
import com.kurento.kmf.media.MediaPipeline;
import com.kurento.kmf.media.MediaPipelineFactory;
import com.kurento.kmf.media.MediaProfileSpecType;
import com.kurento.kmf.media.PlayerEndpoint;
import com.kurento.kmf.media.RecorderEndpoint;
import com.kurento.kmf.media.RtpEndpoint;
import com.kurento.kmf.media.events.EndOfStreamEvent;
import com.kurento.kmf.media.events.MediaEventListener;

/**
 * This example shows the use of the Kurento Media API inside Mobicents SIP Servlets
 * 
 * @author Pablo Couñago
 *
 */
public class RecorderSipServlet extends SipServlet {
	
	private static final long serialVersionUID = 1L;

	private static Log logger = LogFactory.getLog(RecorderSipServlet.class);
	
	public static final String TARGET = "file:///tmp/recording";
	
	@Autowired
	private MediaPipelineFactory mpf;
	private MediaPipeline mp;
	
	private RtpEndpoint rtp;
	private RecorderEndpoint recorder;
	private PlayerEndpoint player;
	private JackVaderFilter filter;
	
	private SipURI fromURI;
	private SipURI toURI;
	private SipServletRequest bye;
	
	public RecorderSipServlet() {
		//Create the Spring context
	    ApplicationContext context = new FileSystemXmlApplicationContext(new String[] {"file:/home/pcounhago/kmf-media-config.xml"});
	    //Inject the MediaPipelineFactory bean
	    mpf = context.getBean(MediaPipelineFactory.class);
	    //MediaPipeline
		mp = mpf.create();
	}
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		logger.info("the RecorderSipServlet servlet has been started");
		super.init(servletConfig);
	}

	@Override
	protected void doInvite(SipServletRequest request) throws ServletException,
			IOException {
		logger.info("Got request:\n"+request.toString());
		
		fromURI = (SipURI)request.getFrom().getURI();
		toURI = (SipURI)request.getTo().getURI();
		
		MediaProfileSpecType mediaProfileSpecType = MediaProfileSpecType.WEBM;

		if (toURI.getUser().equals("loop")){
		//Loopback demo
			//Create the endpoints and connect them
			rtp = mp.newRtpEndpoint().build();
			rtp.connect(rtp);
		} else if (toURI.getUser().equals("record")){
		//Recorder demo
			//Create the endpoints and connect them
			rtp = mp.newRtpEndpoint().build();
			recorder = mp.newRecorderEndpoint(TARGET).withMediaProfile(mediaProfileSpecType).build();
			rtp.connect(rtp);
			rtp.connect(recorder);
		} else if (toURI.getUser().equals("jackvader")){
		//JackVader demo
			//Create the endpoints and connect them
			rtp = mp.newRtpEndpoint().build();
			filter = mp.newJackVaderFilter().build();
			rtp.connect(filter);
			filter.connect(rtp);
		} else {
		//Video player demo
			//Check whether the requested file exists in the video directory or not
			File f = new File("/home/pcounhago/"+toURI.getUser());
			if(f.exists() && !f.isDirectory()) {
				//Create the endpoints and connect them
				rtp = mp.newRtpEndpoint().build();
				player = mp.newPlayerEndpoint("file:///home/pcounhago/"+toURI.getUser()).build();
				player.connect(rtp);
				//Start playing
				player.play();
				
				//Terminate SIP session when EOS received
				player.addEndOfStreamListener(new MediaEventListener<EndOfStreamEvent>() {
					@Override
					public void onEvent(EndOfStreamEvent event) {
						try {
							bye.send();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
			} else {
				SipServletResponse not_found = request.createResponse(SipServletResponse.SC_NOT_FOUND);
				not_found.send();
				return;
			}
		}
		
		//Get SDP offer from the INVITE request
		String remoteSDP = new String(request.getRawContent(),"UTF-8");
		logger.info("Remote SDP record:\n"+remoteSDP+"\n");
		
		//Get the negotiated SDP answer from the KMS
		String localSDP = rtp.processOffer(remoteSDP);
		logger.info("Local SDP record:\n"+localSDP+"\n");
		
		//Send TRYING
		SipServletResponse trying = request.createResponse(SipServletResponse.SC_TRYING);
		trying.send();
		//Send RINGING
		SipServletResponse ringing = request.createResponse(SipServletResponse.SC_RINGING);
		ringing.send();
		//Send 200 OK with negotiated SDP record
		SipServletResponse ok = request.createResponse(SipServletResponse.SC_OK);
		ok.setContent(localSDP,"application/sdp");
		ok.send();		
	}
	
	@Override
	protected void doSuccessResponse(SipServletResponse resp) throws ServletException,
			IOException {
		//Release KMS resources
		rtp.release();
		if (recorder != null) recorder.release();
		if (player != null) player.release();
		if (filter != null) filter.release();
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException,
			IOException {
		//Send 200 OK response
		SipServletResponse sipServletResponse = request.createResponse(SipServletResponse.SC_OK);
		sipServletResponse.send();
		
		//Release KMS resources
		rtp.release();
		if (recorder != null) recorder.release();
		if (player != null) player.release();
		if (filter != null) filter.release();
	}
	
	@Override
	protected void doAck(SipServletRequest request){
		fromURI = (SipURI)request.getFrom().getURI();
		toURI = (SipURI)request.getTo().getURI();
		
		//Create BYE request
		bye = request.getSession().createRequest("BYE");
		fromURI.setHost("172.16.78.221");
		fromURI.setPort(5060);
		bye.setRequestURI(fromURI);
	}
}
