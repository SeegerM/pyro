package de.hpi.isg.pyro.model;

import de.hpi.isg.pyro.util.BitSets;
import de.hpi.isg.pyro.util.VerticalMap;
import de.metanome.algorithm_integration.ColumnCombination;
import de.metanome.algorithm_integration.ColumnIdentifier;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Represents the schema of a relational table.
 *
 * @see RelationData
 */
public class RelationSchema implements Serializable {

    protected final String name;

    protected final List<Column> columns;

    private final boolean isNullEqualNull;

    public final Vertical emptyVertical = Vertical.emptyVertical(this);

    public RelationSchema(String name, boolean isNullEqualNull) {
        this.name = name;
        this.columns = new ArrayList<>();
        this.isNullEqualNull = isNullEqualNull;
    }

    public String getName() {
        return this.name;
    }

    public List<Column> getColumns() {
        return this.columns;
    }

    public ColumnIdentifier getColumnIdentifier(int index) {
        return new ColumnIdentifier(this.getName(), this.columns.get(index).getName());
    }

    public ColumnCombination getColumnCombination(BitSet indices) {
        return this.getColumnCombination(BitSets.toIntArray(indices));
    }


    public ColumnCombination getColumnCombination(int... indices) {
        ColumnIdentifier[] columnIdentifiers = new ColumnIdentifier[indices.length];
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            columnIdentifiers[i] = this.getColumnIdentifier(index);
        }
        return new ColumnCombination(columnIdentifiers);
    }

    public Vertical getVertical(int... indices) {
        if (indices.length == 0) throw new IllegalArgumentException();

        if (indices.length == 1) {
            return this.columns.get(indices[0]);
        }

        BitSet bitSet = new BitSet(this.getNumColumns());
        for (int i = 0; i < indices.length; i++) {
            bitSet.set(indices[i]);
        }
        return this.getVertical(bitSet);
    }

    public Vertical getVertical(List<Integer> indices) {
        if (indices.isEmpty()) return emptyVertical;

        if (indices.size() == 1) {
            return this.columns.get(indices.get(0));
        }

        BitSet bitSet = new BitSet(this.getNumColumns());
        for (Integer index : indices) {
            bitSet.set(index);
        }
        return this.getVertical(bitSet);
    }

    public Vertical getVertical(BitSet indices) {
        if (indices.isEmpty()) return this.emptyVertical;

        if (indices.cardinality() == 1) {
            return this.columns.get(indices.nextSetBit(0));
        }

        return new de.hpi.isg.pyro.model.ColumnCombination(indices, this);
    }

    public void shuffleColumns() {
        throw new UnsupportedOperationException();
    }

    public Column getColumn(String name) {
        for (Column column : this.columns) {
            if (column.getName().equals(name)) {
                return column;
            }
        }
        return null;
    }

    /**
     * Append a {@link de.hpi.isg.mdms.model.targets.Column} to this instance.
     * @param name the name of the new {@link de.hpi.isg.mdms.model.targets.Column}
     */
    public void appendColumn(String name) {
        this.columns.add(new Column(this, name, this.columns.size()));
    }

    public Column getColumn(int index) {
        return this.columns.get(index);
    }

    public int getNumColumns() {
        return this.columns.size();
    }

    public RelationSchema copy() {
        throw new UnsupportedOperationException();
    }

    public boolean isNullEqualNull() {
        return this.isNullEqualNull;
    }


    /**
     * Calculate the minimum hitting set for the given {@code verticals}.
     *
     * @param verticals       whose minimum hitting set is requested
     * @param pruningFunction tells whether an intermittent hitting set should be pruned
     * @return the minimum hitting set
     */
    public Collection<Vertical> calculateHittingSet(
            Collection<Vertical> verticals,
            Predicate<Vertical> pruningFunction) {

        List<Vertical> sortedVerticals = new ArrayList<>(verticals);
        sortedVerticals.sort(Comparator.comparing(Vertical::getArity).reversed());
        VerticalMap<Vertical> consolidatedVerticals = new VerticalMap<>(this);

        VerticalMap<Vertical> hittingSet = new VerticalMap<>(this);
        hittingSet.put(this.emptyVertical, this.emptyVertical);

        // Now, continuously refine these escaped LHS.
        for (Vertical vertical : sortedVerticals) {
            if (!consolidatedVerticals.getSubsetEntries(vertical).isEmpty()) continue;
            consolidatedVerticals.put(vertical, vertical);

            // All hitting set member that are disjoint from the vertical are invalid.
            ArrayList<Vertical> invalidHittingSetMembers = hittingSet.getSubsetKeys(vertical.invert());
            invalidHittingSetMembers.sort(Comparator.comparing(Vertical::getArity));

            // Remove the invalid hitting set members.
            for (Vertical invalidHittingSetMember : invalidHittingSetMembers) {
                hittingSet.remove(invalidHittingSetMember);
            }

            // Add corrected hitting set members.
            for (Vertical invalidMember : invalidHittingSetMembers) {
                for (int correctiveColumnIndex = vertical.getColumnIndices().nextSetBit(0);
                     correctiveColumnIndex != -1;
                     correctiveColumnIndex = vertical.getColumnIndices().nextSetBit(correctiveColumnIndex + 1)) {

                    Column correctiveColumn = this.getColumn(correctiveColumnIndex);
                    Vertical correctedMember = invalidMember.union(correctiveColumn);

                    // This way, we will never add non-minimal members, because our invalid members are sorted.
                    if (hittingSet.getSubsetEntries(correctedMember).isEmpty()
                            && (pruningFunction == null || !pruningFunction.test(correctedMember))) {
                        hittingSet.put(correctedMember, correctedMember);
                    }
                }
                hittingSet.remove(invalidMember);
            }
        }

        // Produce the result.
        return hittingSet.keySet();
    }
}
