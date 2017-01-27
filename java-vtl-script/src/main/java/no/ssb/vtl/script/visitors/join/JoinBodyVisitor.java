package no.ssb.vtl.script.visitors.join;

import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.parser.VTLBaseVisitor;
import no.ssb.vtl.parser.VTLParser;
import no.ssb.vtl.script.operations.DropOperator;
import no.ssb.vtl.script.operations.FilterOperator;
import no.ssb.vtl.script.operations.KeepOperator;
import no.ssb.vtl.script.operations.RenameOperation;
import no.ssb.vtl.script.operations.join.AbstractJoinOperation;
import no.ssb.vtl.script.operations.join.JoinClause;
import no.ssb.vtl.script.operations.join.WorkingDataset;
import org.antlr.v4.runtime.RuleContext;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.*;

/**
 * Sets up the join clauses in the given {@link AbstractJoinOperation}.
 * <p>
 * The last join clause is returned.
 */
public class JoinBodyVisitor extends VTLBaseVisitor<Function<WorkingDataset, WorkingDataset>> {

    private final AbstractJoinOperation joinOperation;

    public JoinBodyVisitor(AbstractJoinOperation joinOperation) {
        this.joinOperation = checkNotNull(joinOperation);
    }

    @Override
    public JoinClause visitJoinKeepClause(VTLParser.JoinKeepClauseContext ctx) {

        JoinClause keepClause = new JoinClause() {

            @Override
            public WorkingDataset apply(WorkingDataset workingDataset) {
                JoinKeepClauseVisitor visitor = new JoinKeepClauseVisitor(workingDataset);
                KeepOperator keep = visitor.visit(ctx);
                return new WorkingDataset() {
                    @Override
                    public DataStructure getDataStructure() {
                        return keep.getDataStructure();
                    }

                    @Override
                    public Stream<Tuple> get() {
                        return keep.get();
                    }
                };
            }
        };

        return keepClause;

    }

    @Override
    public JoinClause visitJoinRenameClause(VTLParser.JoinRenameClauseContext ctx) {
        JoinClause renameClause = new JoinClause() {

            @Override
            public WorkingDataset apply(WorkingDataset workingDataset) {
                JoinRenameClauseVisitor visitor = new JoinRenameClauseVisitor(workingDataset);
                RenameOperation renameOperator = visitor.visit(ctx);
                return new WorkingDataset() {
                    @Override
                    public DataStructure getDataStructure() {
                        return renameOperator.getDataStructure();
                    }

                    @Override
                    public Stream<Tuple> get() {
                        return renameOperator.get();
                    }
                };
            }
        };

        return renameClause;
    }

    @Override
    public JoinClause visitJoinDropClause(VTLParser.JoinDropClauseContext ctx) {

        JoinClause dropClause = new JoinClause() {

            @Override
            public WorkingDataset apply(WorkingDataset workingDataset) {
                JoinDropClauseVisitor visitor = new JoinDropClauseVisitor(workingDataset);
                DropOperator drop = visitor.visit(ctx);
                return new WorkingDataset() {
                    @Override
                    public DataStructure getDataStructure() {
                        return drop.getDataStructure();
                    }

                    @Override
                    public Stream<Tuple> get() {
                        return drop.get();
                    }
                };
            }
        };

        return dropClause;
    }

    @Override
    public JoinClause visitJoinCalcClause(VTLParser.JoinCalcClauseContext ctx) {
        String variableName = ctx.varID().getText();

        // TODO: Spec does not specify what is the default role.
        String variableRole = Optional.ofNullable(ctx.role()).map(RuleContext::getText).orElse("MEASURE");

        JoinCalcClauseVisitor joinCalcClauseVisitor = new JoinCalcClauseVisitor();
        Function<Dataset.Tuple, Object> clauseFunction = joinCalcClauseVisitor.visit(ctx);
        JoinClause calcClause = new JoinClause() {

            @Override
            public WorkingDataset apply(WorkingDataset workingDataset) {
                return new WorkingDataset() {
                    @Override
                    public DataStructure getDataStructure() {
                        DataStructure structure = workingDataset.getDataStructure();
                        structure.addComponent(variableName, Component.Role.MEASURE, Number.class);
                        return structure;
                    }

                    @Override
                    public Stream<Tuple> get() {
                        return workingDataset.get()
                                .map(tuple -> {
                                    tuple.add(getDataStructure().wrap(variableName, clauseFunction.apply(tuple)));
                                    return tuple;
                                });
                    }
                };
            }
        };

        return calcClause;
    }
    
    @Override
    public JoinClause visitJoinFilterClause(VTLParser.JoinFilterClauseContext ctx) {
        JoinClause filterClause = workingDataset -> {
            JoinFilterClauseVisitor visitor = new JoinFilterClauseVisitor(workingDataset);
            FilterOperator filter = visitor.visit(ctx);
            return new WorkingDataset() {
                @Override
                public DataStructure getDataStructure() {
                    return filter.getDataStructure();
                }
            
                @Override
                public Stream<Tuple> get() {
                    return filter.get();
                }
            };
        };
    
        return filterClause;
    }
    
    @Override
    protected Function<WorkingDataset, WorkingDataset> aggregateResult(
            Function<WorkingDataset, WorkingDataset> aggregate, Function<WorkingDataset, WorkingDataset> nextResult) {
    
        return aggregate.andThen(nextResult);
    }
    
    @Override
    protected Function<WorkingDataset, WorkingDataset> defaultResult() {
        return Function.identity();
    }
}
