/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.services.sync;

import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.gravitee.gateway.env.GatewayConfiguration;
import io.gravitee.gateway.handlers.api.manager.ApiManager;
import io.gravitee.gateway.services.sync.apikeys.ApiKeysCacheService;
import io.gravitee.gateway.services.sync.handler.SyncHandler;
import io.gravitee.gateway.services.sync.healthcheck.ApiSyncProbe;
import io.gravitee.gateway.services.sync.subscriptions.SubscriptionsCacheService;
import io.gravitee.node.api.healthcheck.ProbeManager;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.EnvironmentRepository;
import io.gravitee.repository.management.api.OrganizationRepository;
import io.gravitee.repository.management.model.Environment;
import io.gravitee.repository.management.model.Organization;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncService extends AbstractService implements Runnable {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(SyncService.class);

    private final static String PATH = "/sync";

    @Autowired
    private TaskScheduler scheduler;

    @Value("${services.sync.cron:*/5 * * * * *}")
    private String cronTrigger;

    @Value("${services.sync.enabled:true}")
    private boolean enabled;

    @Value("${services.local.enabled:false}")
    private boolean localRegistryEnabled;

    @Autowired
    private ApiManager apiManager;

    @Autowired
    private SyncManager syncStateManager;

    @Autowired
    @Qualifier("managementRouter")
    private Router router;

    @Autowired
    private ApiKeysCacheService apiKeysCacheService;

    @Autowired
    private SubscriptionsCacheService subscriptionsCacheService;

    @Autowired
    private ProbeManager probeManager;

    @Autowired
    private ApiSyncProbe apiSyncProbe;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GatewayConfiguration configuration;

    private Set<Environment> environments;

    private ScheduledFuture<?> schedule;

    @Override
    protected void doStart() throws Exception {
        if (!localRegistryEnabled) {
            if (enabled) {
                super.doStart();

                logger.info("Sync service has been initialized with cron [{}]", cronTrigger);

                this.environments = getTargetedEnvironments();

                probeManager.register(this.apiSyncProbe);

                logger.info("Associate a new HTTP handler on {}", PATH);

                // Create and associate handler
                SyncHandler syncHandler = new SyncHandler();
                applicationContext.getAutowireCapableBeanFactory().autowireBean(syncHandler);
                router.get(PATH).produces(MediaType.APPLICATION_JSON).handler(syncHandler);

                // Start tasks
                apiKeysCacheService.start();
                subscriptionsCacheService.start();

                // Force refresh on APIs
                apiManager.refresh();

                schedule = scheduler.schedule(this, new CronTrigger(cronTrigger));
            } else {
                logger.warn("Sync service is disabled");
            }
        } else {
            logger.warn("Sync service is disabled because local registry mode is enabled");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (schedule != null) {
            schedule.cancel(true);
        }

        apiKeysCacheService.stop();
        subscriptionsCacheService.stop();

        super.doStop();
    }

    @Override
    public void run() {
        List<String> environmentsIds = environments.stream().map(Environment::getId).collect(Collectors.toList());
        syncStateManager.refresh(environmentsIds);
    }

    public boolean isAllApisSync() {
        return syncStateManager.isAllApisSync();
    }

    @Override
    protected String name() {
        return "Gateway Sync Service";
    }

    private Set<Environment> getTargetedEnvironments() throws TechnicalException {
        final Optional<List<String>> optOrganizationsList = configuration.organizations();
        final Optional<List<String>> optEnvironmentsList = configuration.environments();

        Set<String> organizationsIds = new HashSet<>();
        Set<String> environmentsHrids = new HashSet<>();

        if (optOrganizationsList.isPresent()) {
            List<String> organizationsHrids = optOrganizationsList.get();
            final Set<Organization> organizations = organizationRepository.findByHrids(new HashSet<>(organizationsHrids));
            organizationsIds = organizations.stream().map(Organization::getId).collect(Collectors.toSet());

            checkOrganizations(organizationsHrids, organizations);
        }

        if (optEnvironmentsList.isPresent()) {
            environmentsHrids = new HashSet<>(optEnvironmentsList.get());
        }

        Set<Environment> environments = environmentRepository.findByOrganizationsAndHrids(organizationsIds, environmentsHrids);

        checkEnvironments(environmentsHrids, environments);

        return environments;
    }

    private void checkOrganizations(List<String> organizationsHrids, Set<Organization> organizations) {
        if (organizationsHrids.size() != organizations.size()) {
            final Set<String> hrids = new HashSet<>(organizationsHrids);
            final Set<String> returnedHrids = organizations.stream().flatMap(org -> org.getHrids().stream()).collect(Collectors.toSet());
            hrids.removeAll(returnedHrids);
            logger.warn("No organization found for hrids {}", hrids);
        }
    }

    private void checkEnvironments(Set<String> environmentsHrids, Set<Environment> environments) {

        final Set<String> returnedHrids = environments
                .stream()
                .flatMap(env -> env.getHrids().stream())
                .filter(environmentsHrids::contains)
                .collect(Collectors.toSet());

        if (environmentsHrids.size() != returnedHrids.size()) {
            final Set<String> hrids = new HashSet<>(environmentsHrids);
            hrids.removeAll(returnedHrids);
            logger.warn("No environment found for hrids {}", hrids);
        }
    }
}
