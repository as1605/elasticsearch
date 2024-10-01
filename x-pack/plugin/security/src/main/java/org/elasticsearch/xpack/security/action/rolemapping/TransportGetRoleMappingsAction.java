/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.action.rolemapping;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.injection.guice.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.security.action.rolemapping.GetRoleMappingsAction;
import org.elasticsearch.xpack.core.security.action.rolemapping.GetRoleMappingsRequest;
import org.elasticsearch.xpack.core.security.action.rolemapping.GetRoleMappingsResponse;
import org.elasticsearch.xpack.core.security.authc.support.mapper.ExpressionRoleMapping;
import org.elasticsearch.xpack.security.authc.support.mapper.ClusterStateRoleMapper;
import org.elasticsearch.xpack.security.authc.support.mapper.NativeRoleMappingStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransportGetRoleMappingsAction extends HandledTransportAction<GetRoleMappingsRequest, GetRoleMappingsResponse> {

    private final NativeRoleMappingStore roleMappingStore;
    private final ClusterStateRoleMapper clusterStateRoleMapper;

    @Inject
    public TransportGetRoleMappingsAction(
        ActionFilters actionFilters,
        TransportService transportService,
        NativeRoleMappingStore nativeRoleMappingStore,
        ClusterStateRoleMapper clusterStateRoleMapper
    ) {
        super(
            GetRoleMappingsAction.NAME,
            transportService,
            actionFilters,
            GetRoleMappingsRequest::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.roleMappingStore = nativeRoleMappingStore;
        this.clusterStateRoleMapper = clusterStateRoleMapper;
    }

    @Override
    protected void doExecute(Task task, final GetRoleMappingsRequest request, final ActionListener<GetRoleMappingsResponse> listener) {
        final Set<String> names;
        if (request.getNames() == null || request.getNames().length == 0) {
            names = null;
        } else {
            names = new HashSet<>(Arrays.asList(request.getNames()));
        }
        // TODO sort so we get deterministic order; don't think ExpressionRoleMapping have a compareTo currently
        final List<ExpressionRoleMapping> clusterStateRoleMappings = new ArrayList<>(clusterStateRoleMapper.getMappings(names));
        roleMappingStore.getRoleMappings(names, ActionListener.wrap(mappings -> {
            if (clusterStateRoleMappings.isEmpty()) {
                listener.onResponse(new GetRoleMappingsResponse(mappings));
                return;
            }

            if (mappings.isEmpty()) {
                listener.onResponse(new GetRoleMappingsResponse(clusterStateRoleMappings));
                return;
            }

            // Native role mappings must come first
            final List<ExpressionRoleMapping> combined = new ArrayList<>(mappings);
            combined.addAll(clusterStateRoleMappings);
            listener.onResponse(new GetRoleMappingsResponse(combined.toArray(new ExpressionRoleMapping[0])));
        }, listener::onFailure));
    }
}
