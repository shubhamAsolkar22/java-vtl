package no.ssb.vtl.script.support;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.VTLObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DatapointNormalizerTest extends RandomizedTest {

    private DataStructure structure;
    private List<VTLObject> datum;

    @Before
    public void setUp()  {
        this.structure = DataStructure.builder()
                .put("A", Component.Role.IDENTIFIER, String.class)
                .put("B", Component.Role.IDENTIFIER, String.class)
                .put("C", Component.Role.IDENTIFIER, String.class)
                .put("D", Component.Role.IDENTIFIER, String.class)
                .put("E", Component.Role.IDENTIFIER, String.class)
                .put("F", Component.Role.IDENTIFIER, String.class)
                .put("G", Component.Role.IDENTIFIER, String.class)
                .put("H", Component.Role.IDENTIFIER, String.class)
                .put("I", Component.Role.IDENTIFIER, String.class)
                .put("J", Component.Role.IDENTIFIER, String.class)
                .put("K", Component.Role.IDENTIFIER, String.class)
                .build();

        this.datum = Stream.of("a", "b", "c", "d", "e","f","g","h","i","j","k").map(VTLObject::of).collect(Collectors.toList());

    }

    @Test
    @Repeat(iterations = 100)
    public void testApply() {

        int size = between(1, 10);

        DataStructure from = reduceStructure(structure, size);
        DataStructure shuffledStructure = shuffleStructure(from, getRandom());

        DatapointNormalizer normalizer = new DatapointNormalizer(from, shuffledStructure);

        DataPoint datapoint = DataPoint.create(datum.subList(0, size));
        DataPoint result = normalizer.apply(datapoint);

        assertThat(
                from.asMap(datapoint).values()
        ).containsExactlyElementsOf(
                shuffledStructure.asMap(result).values()
        );

    }

    private DataStructure shuffleStructure(DataStructure structure, Random random) {
        List<Map.Entry<String, Component>> list = Lists.newArrayList(structure.entrySet());
        Collections.shuffle(list, getRandom());
        return DataStructure.copyOf(ImmutableMap.copyOf(list)).build();
    }

    private DataStructure reduceStructure(DataStructure structure, int size) {
        List<Map.Entry<String, Component>> list = Lists.newArrayList(structure.entrySet()).subList(0, size);
        return DataStructure.copyOf(ImmutableMap.copyOf(list)).build();
    }
}