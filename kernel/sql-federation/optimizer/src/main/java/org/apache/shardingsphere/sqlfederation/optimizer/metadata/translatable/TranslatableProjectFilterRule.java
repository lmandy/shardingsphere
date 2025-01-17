/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sqlfederation.optimizer.metadata.translatable;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilderFactory;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Planner rule for pushing projections and filters into table scan.
 */
public class TranslatableProjectFilterRule extends RelOptRule {
    
    public static final TranslatableProjectFilterRule INSTANCE = new TranslatableProjectFilterRule(RelFactories.LOGICAL_BUILDER);
    
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\$[A-Za-z]");
    
    private static final Pattern CONDITION_FUNCTION_PATTERN = Pattern.compile("[A-Za-z_]+\\.[A-Za-z_]+\\(.*\\)");
    
    private static final Pattern CONDITION_COMPLEX_PATTERN = Pattern.compile("NEGATED POSIX REGEX CASE SENSITIVE");
    
    public TranslatableProjectFilterRule(final RelBuilderFactory relBuilderFactory) {
        super(operand(LogicalProject.class, operand(LogicalFilter.class, operand(TranslatableTableScan.class, none()))), relBuilderFactory, "TranslatableProjectFilterRule");
    }
    
    @Override
    public void onMatch(final RelOptRuleCall call) {
        LogicalProject project = call.rel(0);
        LogicalFilter filter = call.rel(1);
        TranslatableTableScan scan = call.rel(2);
        int[] fields = getProjectFields(project.getProjects());
        if (0 == fields.length) {
            return;
        }
        call.transformTo(new TranslatableTableScan(scan.getCluster(), scan.getTable(), scan.getTranslatableTable(), Collections.singletonList(filter.getCondition()), fields));
    }
    
    private int[] getProjectFields(final List<RexNode> rexNodes) {
        int[] result = new int[rexNodes.size()];
        for (int index = 0; index < rexNodes.size(); index++) {
            RexNode exp = rexNodes.get(index);
            if (exp instanceof RexInputRef) {
                result[index] = ((RexInputRef) exp).getIndex();
            } else {
                return new int[0];
            }
        }
        return result;
    }
    
    @Override
    public boolean matches(final RelOptRuleCall call) {
        LogicalFilter filter = call.rel(1);
        RexCall condition = (RexCall) filter.getCondition();
        for (RexNode each : condition.getOperands()) {
            if (CONDITION_PATTERN.matcher(each.toString()).find()
                    || CONDITION_FUNCTION_PATTERN.matcher(each.toString()).find()
                    || CONDITION_COMPLEX_PATTERN.matcher(each.toString()).find()) {
                return false;
            }
        }
        return true;
    }
}
