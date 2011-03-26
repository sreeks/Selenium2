/*
Copyright 2007-2011 WebDriver committers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.openqa.grid.selenium.proxy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.exception.RemoteException;
import org.openqa.grid.internal.exception.RemoteNotReachableException;
import org.openqa.grid.internal.listeners.CommandListener;
import org.openqa.grid.internal.listeners.SelfHealingProxy;
import org.openqa.grid.internal.listeners.TimeoutListener;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.utils.WebProxyHtmlRenderer;

public abstract class WebRemoteProxy extends RemoteProxy implements TimeoutListener, SelfHealingProxy, CommandListener {

	public WebRemoteProxy(RegistrationRequest request) {
		super(request);
	}

	public abstract void beforeRelease(TestSession session);

	public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
		session.put("lastCommand", request.getMethod() + " - " + request.getPathInfo() + " executing ...");
	}

	public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
		session.put("lastCommand", request.getMethod() + " - " + request.getPathInfo() + " executed.");
	}

	private final HtmlRenderer renderer = new WebProxyHtmlRenderer(this);

	@Override
	public HtmlRenderer getHtmlRender() {
		return renderer;
	}

	/*
	 * Self Healing part.Polls the remote, and marks it down if it cannot be reached twice in a row.
	 */
	private boolean down = false;
	private boolean poll = true;
	int nbFailedPoll = 0;
	// TODO freynaud
	private List<RemoteException> errors = new CopyOnWriteArrayList<RemoteException>();
	private Thread pollingThread = new Thread(new Runnable() {
		public void run() {
			while (poll) {
				try {
					Thread.sleep(10000);
					if (!isAlive()) {
						if (!down) {
							nbFailedPoll++;
							if (nbFailedPoll>=2){
								addNewEvent(new RemoteNotReachableException("Cannot reach the remote."));
							}
						}
					} else {
						down = false;
						System.out.println("back up");
						nbFailedPoll=0;
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	});

	public abstract boolean isAlive();

	public void startPolling() {
		pollingThread.start();
	}

	public void stopPolling() {
		poll = false;
		pollingThread.interrupt();
	}

	public void addNewEvent(RemoteException event) {
		errors.add(event);
		onEvent(errors, event);

	}

	public void onEvent(List<RemoteException> events, RemoteException lastInserted) {
		for (RemoteException e : events) {
			if (e instanceof RemoteNotReachableException) {
				down = true;
				System.out.println("down");
				this.errors.clear();
			}
		}
	}

	/**
	 * overwrites the session allocation to discard the proxy that are down. 
	 */
	@Override
	public TestSession getNewSession(Map<String, Object> requestedCapability) {
		if (down){
			return null;
		}
		return super.getNewSession(requestedCapability);
	}
	
	public boolean isDown(){
		return down;
	}

}
