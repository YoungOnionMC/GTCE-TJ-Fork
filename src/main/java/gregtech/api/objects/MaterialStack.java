package gregtech.api.objects;

import gregtech.api.enums.material.types.Material;
import gregtech.api.enums.material.Materials;

public class MaterialStack {

    public Material material;
    public long amount;

    public MaterialStack(Material material, long amount) {
        this.material = material;
        this.amount = amount;
    }

    public MaterialStack copy(long amount) {
        return new MaterialStack(material, amount);
    }

    public MaterialStack copy() {
        return new MaterialStack(material, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MaterialStack that = (MaterialStack) o;

        if (amount != that.amount) return false;
        return material.equals(that.material);
    }

    @Override
    public int hashCode() {
        return material.hashCode();
    }

    @Override
    public String toString() {
        String string = Long.toString(amount);
        if(material.materialComponents.size() > 1) {
            string += '(' + material.chemicalFormula.get() + ')';
        } else string += material.chemicalFormula.get();
        return string;
    }

}