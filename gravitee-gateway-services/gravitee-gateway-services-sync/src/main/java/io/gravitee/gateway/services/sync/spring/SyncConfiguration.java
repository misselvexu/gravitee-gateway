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
package io.gravitee.gateway.services.sync.spring;

import io.gravitee.gateway.services.sync.SyncManager;
import io.gravitee.gateway.services.sync.apikeys.spring.ApiKeysConfiguration;
import io.gravitee.gateway.services.sync.cache.CacheManager;
import io.gravitee.gateway.services.sync.healthcheck.ApiSyncProbe;
import io.gravitee.gateway.services.sync.subscriptions.spring.SubscriptionsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        ApiKeysConfiguration.class,
        SubscriptionsConfiguration.class
})
public class SyncConfiguration {

    @Bean
    public SyncManager syncStateManager() {
        return new SyncManager();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("gio.sync-");
        return scheduler;
    }

    @Bean
    public CacheManager cacheManager() {
        return new CacheManager();
    }

    @Bean
    public ApiSyncProbe apisProbe() {
        return new ApiSyncProbe();
    }
}
