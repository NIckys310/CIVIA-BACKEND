package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.civia.service.ModelQuantities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Cómputo de las estructuras del catálogo ampliado del modelador 3D. */
class ModelStructuresTest {

    private final ObjectMapper json = new ObjectMapper();

    private ModelQuantities.Takeoff takeoff(String elements) throws Exception {
        return ModelQuantities.of(json.readTree("{\"elements\":[" + elements + "]}"));
    }

    @Test
    void pileIsMeasuredAsAConcreteCylinder() throws Exception {
        var t = takeoff("{\"type\":\"pile\",\"material\":\"concreto\",\"params\":{\"diameter\":0.5,\"length\":12}}").totals();
        assertThat(t.concreteM3()).isCloseTo(Math.PI * 0.0625 * 12, within(0.001));
        assertThat(t.elements()).isEqualTo(1);
    }

    @Test
    void trussAddsChordsVerticalsAndDiagonals() throws Exception {
        var t = takeoff("{\"type\":\"truss\",\"material\":\"madera\",\"params\":{\"span\":10,\"height\":2,\"panels\":8,\"size\":0.1}}").totals();
        assertThat(t.woodM3()).isCloseTo(0.406, within(0.001));
    }

    @Test
    void hipRoofAreaFollowsThePitch() throws Exception {
        var t = takeoff("{\"type\":\"hip_roof\",\"material\":\"teja\",\"params\":{\"width\":8,\"length\":12,\"pitch\":25}}").totals();
        assertThat(t.roofM2()).isCloseTo(96 / Math.cos(Math.toRadians(25)), within(0.001));
    }

    @Test
    void openingsCountAndGlazingGoToTheirOwnLines() throws Exception {
        var t = takeoff("{\"type\":\"sliding_window\",\"material\":\"vidrio\",\"params\":{\"width\":1.8,\"height\":1.2}},"
                + "{\"type\":\"garage_door\",\"material\":\"acero\",\"params\":{\"width\":3,\"height\":2.2}},"
                + "{\"type\":\"curtain_wall\",\"material\":\"vidrio\",\"params\":{\"length\":6,\"height\":3.5}}").totals();
        assertThat(t.windows()).isEqualTo(1);
        assertThat(t.doors()).isEqualTo(1);
        assertThat(t.steelKg()).isZero();
        assertThat(t.glassM2()).isCloseTo(1.8 * 1.2 + 21, within(0.001));
    }

    @Test
    void wallLikeElementsUseAreaUnlessTheyAreConcrete() throws Exception {
        var t = takeoff("{\"type\":\"partition\",\"material\":\"pintura\",\"params\":{\"length\":3,\"height\":2.4,\"thickness\":0.1}},"
                + "{\"type\":\"parapet\",\"material\":\"concreto\",\"params\":{\"length\":6,\"height\":1,\"thickness\":0.15}}").totals();
        assertThat(t.masonryM2()).isCloseTo(7.2, within(0.001));
        assertThat(t.concreteM3()).isCloseTo(0.9, within(0.001));
    }

    @Test
    void finishesHaveTheirOwnTotalAndTheLineIsLabelled() throws Exception {
        var takeoff = takeoff("{\"type\":\"ceiling\",\"material\":\"pintura\",\"params\":{\"width\":5,\"length\":4}}");
        assertThat(takeoff.totals().finishM2()).isCloseTo(20, within(0.001));
        assertThat(takeoff.lines()).singleElement().satisfies(line -> {
            assertThat(line.label()).isEqualTo("Cielos rasos");
            assertThat(line.unit()).isEqualTo("m²");
        });
    }

    @Test
    void unknownTypesAreIgnored() throws Exception {
        assertThat(takeoff("{\"type\":\"nave_espacial\",\"params\":{}}").totals().elements()).isZero();
    }
}
