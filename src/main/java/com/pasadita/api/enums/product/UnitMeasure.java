package com.pasadita.api.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UnitMeasure {
    PIEZA("H87", "Pieza"),
    PORCION("H87", "Pieza"),
    KILOGRAMO("KGM", "Kilogramo"),
    GRAMO("GRM", "Gramo"),
    LITRO("LTR", "Litro"),
    MILILITRO("MLT", "Mililitro"),
    ARPILLA("XSA", "Saco"),
    CAJA("XBX", "Caja");

    private final String satCode;
    private final String satName;
}
