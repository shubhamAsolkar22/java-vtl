package no.ssb.vtl.script.operations.repeat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.Order;
import no.ssb.vtl.model.StaticDataset;
import no.ssb.vtl.script.VTLDataset;
import no.ssb.vtl.script.operations.hierarchy.HierarchyOperation;
import no.ssb.vtl.script.operations.join.InnerJoinOperation;
import org.junit.Test;

import static no.ssb.vtl.model.Component.Role.ATTRIBUTE;
import static no.ssb.vtl.model.Component.Role.IDENTIFIER;
import static no.ssb.vtl.model.Component.Role.MEASURE;

public class RepeatOperationTest {

    @Test
    public void testInnerJoin() {
        Dataset data1 = StaticDataset.create()
                .addComponent("year", IDENTIFIER, Long.class)
                .addComponent("id", IDENTIFIER, Long.class)
                .addComponent("measure", MEASURE, String.class)
                .addComponent("attribute", ATTRIBUTE, String.class)

                .addPoints(2000L, 1L, "m1", "t1-2000")
                .addPoints(2000L, 2L, "m2", "t1-2000")
                .addPoints(2000L, 3L, "m3", "t1-2000")

                .addPoints(2001L, 1L, "m1", "t1-2001")
                .addPoints(2001L, 2L, "m2", "t1-2001")
                .addPoints(2001L, 3L, "m3", "t1-2001")
                .addPoints(2001L, 4L, "m4", "t1-2001")

                .addPoints(2003L, 1L, "m1", "t1-2003")
                .addPoints(2003L, 2L, "m2", "t1-2003")
                .addPoints(2003L, 3L, "m3", "t1-2003")
                .addPoints(2003L, 4L, "m4", "t1-2003")

                .addPoints(2004L, 1L, "m1", "t1-2004")
                .addPoints(2004L, 2L, "m2", "t1-2004")
                .addPoints(2004L, 3L, "m3", "t1-2004")

                .build();

        Dataset data2 = StaticDataset.create()
                .addComponent("year", IDENTIFIER, Long.class)
                .addComponent("id", IDENTIFIER, Long.class)
                .addComponent("measure", MEASURE, String.class)
                .addComponent("attribute", ATTRIBUTE, String.class)

                .addPoints(2000L, 1L, "m1", "t2-2000")
                .addPoints(2000L, 2L, "m2", "t2-2000")
                .addPoints(2000L, 3L, "m3", "t2-2000")
                .addPoints(2000L, 4L, "m4", "t2-2000")

                .addPoints(2002L, 1L, "m1", "t2-2002")
                .addPoints(2002L, 2L, "m2", "t2-2002")
                .addPoints(2002L, 3L, "m3", "t2-2002")
                .addPoints(2002L, 4L, "m4", "t2-2002")

                .addPoints(2003L, 1L, "m1", "t2-2003")
                .addPoints(2003L, 2L, "m2", "t2-2003")
                .addPoints(2003L, 3L, "m3", "t2-2003")

                .addPoints(2004L, 1L, "m1", "t2-2004")
                .addPoints(2004L, 2L, "m2", "t2-2004")
                .addPoints(2004L, 3L, "m3", "t2-2004")

                .build();

        RepeatOperation repeatOperation = new RepeatOperation(ImmutableMap.of("t1", data1, "t2", data2), ImmutableSet.of("year"));
        repeatOperation.setBlock(bindings -> {
            VTLDataset t1 = (VTLDataset) bindings.get("t1");
            VTLDataset t2 = (VTLDataset) bindings.get("t2");
            ImmutableMap<String, Dataset> namedDataset = ImmutableMap.of(
                    "t1", t1.get(),
                    "t2", t2.get()
            );
            ImmutableSet<Component> identifier = ImmutableSet.of(
                    t1.get().getDataStructure().get("year"),
                    t1.get().getDataStructure().get("id")
            );
            return VTLDataset.of(new InnerJoinOperation(namedDataset, identifier));
        });
        System.out.println(repeatOperation.getDataStructure());
        repeatOperation.getData(
                Order.create(repeatOperation.getDataStructure())
                        .put("year", Order.Direction.DESC).build()
        ).ifPresent(dataPointStream -> dataPointStream.forEach(System.out::println));


    }

    @Test
    public void testHierarchy() {
        Dataset data1 = StaticDataset.create()
                .addComponent("year", IDENTIFIER, Long.class)
                .addComponent("id", IDENTIFIER, String.class)
                .addComponent("measure", MEASURE, Long.class)
                .addComponent("attribute", ATTRIBUTE, String.class)

                .addPoints(2000L, "m1", 1L , "t1-2000")
                .addPoints(2000L, "m2", 2L , "t1-2000")
                .addPoints(2000L, "m3", 3L , "t1-2000")

                .addPoints(2001L, "m1", 1L , "t1-2001")
                .addPoints(2001L, "m2", 2L , "t1-2001")
                .addPoints(2001L, "m3", 3L , "t1-2001")
                .addPoints(2001L, "m4", 4L , "t1-2001")

                .addPoints(2003L, "m1", 1L , "t1-2003")
                .addPoints(2003L, "m2", 2L , "t1-2003")
                .addPoints(2003L, "m3", 3L , "t1-2003")
                .addPoints(2003L, "m4", 4L , "t1-2003")

                .addPoints(2004L, "m1", 1L , "t1-2004")
                .addPoints(2004L, "m2", 2L , "t1-2004")
                .addPoints(2004L, "m3", 3L , "t1-2004")

                .build();

        StaticDataset hierarchy = StaticDataset.create()
                .addComponent("year", IDENTIFIER, Long.class)
                .addComponent("from", IDENTIFIER, String.class)
                .addComponent("to", IDENTIFIER, String.class)
                .addComponent("sign", IDENTIFIER, String.class)

                .addPoints(2001L, "m1", "total", "+")
                .addPoints(2001L, "m2", "total", "+")
                .addPoints(2001L, "m3", "total", "+")

                .addPoints(2003L, "m1", "total", "+")
                .addPoints(2003L, "m2", "total", "+")

                .addPoints(2004L, "m1", "total", "+")

                .build();

        RepeatOperation repeatOperation = new RepeatOperation(ImmutableMap.of("t1", data1, "hier", hierarchy), ImmutableSet.of("year"));
        repeatOperation.setBlock(bindings -> {
            VTLDataset t1 = (VTLDataset) bindings.get("t1");
            VTLDataset hier = (VTLDataset) bindings.get("hier");
            return VTLDataset.of(new HierarchyOperation(t1.get(), hier.get(), t1.get().getDataStructure().get("id")));
        });

        System.out.println(repeatOperation.getDataStructure());
        repeatOperation.getData().forEach(System.out::println);
        System.out.println(repeatOperation.getDataStructure());
        repeatOperation.getData(
                Order.create(repeatOperation.getDataStructure()                )
                        .put("measure", Order.Direction.DESC)
                        .put("year", Order.Direction.DESC)
                        .build()
        ).get().forEach(System.out::println);

    }
}