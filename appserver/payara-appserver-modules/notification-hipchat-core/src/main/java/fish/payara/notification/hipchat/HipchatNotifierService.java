/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2017 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.notification.hipchat;

import com.google.common.eventbus.Subscribe;
import fish.payara.nucleus.notification.configuration.HipchatNotifier;
import fish.payara.nucleus.notification.configuration.NotifierType;
import fish.payara.nucleus.notification.service.QueueBasedNotifierService;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

/**
 * @author mertcaliskan
 */
@Service(name = "service-hipchat")
@RunLevel(StartupRunLevel.VAL)
public class HipchatNotifierService extends QueueBasedNotifierService<HipchatNotificationEvent,
        HipchatNotifier,
        HipchatNotifierConfiguration,
        HipchatMessageQueue> {

    HipchatNotifierConfigurationExecutionOptions executionOptions;

    HipchatNotifierService() {
        super("hipchat-message-consumer-");
    }

    @Override
    @Subscribe
    public void handleNotification(HipchatNotificationEvent event) {
        if (executionOptions != null && executionOptions.isEnabled()) {
            HipchatMessage message = new HipchatMessage(event, event.getSubject(), event.getMessage());
            queue.addMessage(message);
        }
    }

    @Override
    public void bootstrap() {
        register(NotifierType.HIPCHAT, HipchatNotifier.class, HipchatNotifierConfiguration.class, this);
        executionOptions = (HipchatNotifierConfigurationExecutionOptions) getNotifierConfigurationExecutionOptions();
        if (executionOptions != null && executionOptions.isEnabled()){
            initializeExecutor();
            scheduleExecutor(new HipchatNotificationRunnable(queue, executionOptions));
        }
        
    }

}