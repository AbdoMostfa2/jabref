package org.jabref.logic.bibtex.comparator;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import org.jabref.model.entry.AuthorList;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldProperty;
import org.jabref.model.entry.field.InternalField;

/**
 * This implementation of Comparator takes care of most of the details of sorting BibTeX entries in JabRef. It is
 * structured as a node in a linked list of comparators, where each node can contain a link to a new comparator that
 * decides the ordering (by recursion) if this one can't find a difference. The next node, if any, is given at
 * construction time, and an arbitrary number of nodes can be included. If the entries are equal by this comparator, and
 * there is no next entry, the entries' unique IDs will decide the ordering.
 */
public class EntryComparator implements Comparator<BibEntry> {

    private final Field sortField;
    private final boolean descending;
    private final boolean binary;
    private final Comparator<BibEntry> next;

    /**
     *
     * @param binary true: the presence of fields is checked; false: the content of the fields is compared
     * @param descending true: if the most different entry should get the highest score
     * @param field the field to sort on
     * @param next the next comparator to use (if the current comparator results in equality)
     */
    public EntryComparator(boolean binary, boolean descending, Field field, Comparator<BibEntry> next) {
        this.binary = binary;
        this.sortField = field;
        this.descending = descending;
        this.next = next;
    }

    public EntryComparator(boolean binary, boolean descending, Field field) {
        this.binary = binary;
        this.sortField = field;
        this.descending = descending;
        this.next = null;
    }

    @Override
    public int compare(BibEntry e1, BibEntry e2) {
        // default equals
        // TODO: with the new default equals this does not only return 0 for identical objects,
        //       but for all objects that have the same id and same fields
        if (Objects.equals(e1, e2)) {
            return 0;
        }

        Object f1 = e1.getField(sortField).orElse(null);
        Object f2 = e2.getField(sortField).orElse(null);

        if (binary) {
            // We just separate on set and unset fields:
            if (f1 == null) {
                return f2 == null ? (next == null ? idCompare(e1, e2) : next.compare(e1, e2)) : 1;
            } else {
                return f2 == null ? -1 : (next == null ? idCompare(e1, e2) : next.compare(e1, e2));
            }
        }

        // If the field is author or editor, we rearrange names to achieve that they are
        // sorted according to last name.
        if (sortField.getProperties().contains(FieldProperty.PERSON_NAMES)) {
            if (f1 != null) {
                f1 = AuthorList.fixAuthorForAlphabetization((String) f1).toLowerCase(Locale.ROOT);
            }
            if (f2 != null) {
                f2 = AuthorList.fixAuthorForAlphabetization((String) f2).toLowerCase(Locale.ROOT);
            }
        } else if (sortField.equals(InternalField.TYPE_HEADER)) {
            // Sort by type.
            f1 = e1.getType();
            f2 = e2.getType();
        } else if (sortField.equals(InternalField.KEY_FIELD)) {
            f1 = e1.getCitationKey().orElse(null);
            f2 = e2.getCitationKey().orElse(null);
        } else if (sortField.isNumeric()) {
            try {
                int i1 = Integer.parseInt((String) f1);
                int i2 = Integer.parseInt((String) f2);
                // Ok, parsing was successful. Update f1 and f2:
                f1 = i1;
                f2 = i2;
            } catch (NumberFormatException ex) {
                // Parsing failed. Give up treating these as numbers.
                // TODO: should we check which of them failed, and sort based on that?
            }
        }

        if (f2 == null) {
            if (f1 == null) {
                return next == null ? idCompare(e1, e2) : next.compare(e1, e2);
            } else {
                return 1;
            }
        }

        if (f1 == null) { // f2 != null here automatically
            return -1;
        }

        int result;

        if ((f1 instanceof Integer f1i) && (f2 instanceof Integer f2i)) {
            result = f1i.compareTo(f2i);
        } else if (f2 instanceof Integer integer) {
            Integer f1AsInteger = Integer.valueOf(f1.toString());
            result = f1AsInteger.compareTo(integer);
        } else if (f1 instanceof Integer integer) {
            Integer f2AsInteger = Integer.valueOf(f2.toString());
            result = integer.compareTo(f2AsInteger);
        } else {
            String ours = ((String) f1).toLowerCase(Locale.ROOT);
            String theirs = ((String) f2).toLowerCase(Locale.ROOT);
            int comp = ours.compareTo(theirs);
            result = comp;
        }
        if (result != 0) {
            return descending ? -result : result; // Primary sort.
        }
        if (next == null) {
            return idCompare(e1, e2); // If still equal, we use the unique IDs.
        } else {
            return next.compare(e1, e2); // Secondary sort if existent.
        }
    }

    private static int idCompare(BibEntry b1, BibEntry b2) {
        return b1.getId().compareTo(b2.getId());
    }
}
