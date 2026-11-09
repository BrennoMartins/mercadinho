package br.com.aromasabor.mercadinho.sale.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SaleDiscountAllocator {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;

    private SaleDiscountAllocator() {
    }

    public static List<BigDecimal> allocate(BigDecimal discountAmount, List<BigDecimal> itemSubtotals) {
        BigInteger discountCents = toCents(discountAmount);
        List<BigInteger> itemCents = itemSubtotals.stream()
                .map(SaleDiscountAllocator::toCents)
                .toList();
        BigInteger subtotalCents = itemCents.stream().reduce(ZERO, BigInteger::add);

        if (discountCents.signum() < 0 || discountCents.compareTo(subtotalCents) > 0) {
            throw new IllegalArgumentException("The discount must be between zero and the sale subtotal.");
        }
        if (subtotalCents.signum() == 0) {
            return itemCents.stream().map(ignored -> BigDecimal.ZERO.setScale(2)).toList();
        }

        List<AllocationRemainder> remainders = new ArrayList<>(itemCents.size());
        List<BigInteger> allocations = new ArrayList<>(itemCents.size());
        BigInteger allocatedCents = ZERO;

        for (int index = 0; index < itemCents.size(); index++) {
            BigInteger numerator = discountCents.multiply(itemCents.get(index));
            BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(subtotalCents);
            allocations.add(quotientAndRemainder[0]);
            allocatedCents = allocatedCents.add(quotientAndRemainder[0]);
            remainders.add(new AllocationRemainder(index, quotientAndRemainder[1]));
        }

        int remainingCents = discountCents.subtract(allocatedCents).intValueExact();
        remainders.sort(Comparator.comparing(AllocationRemainder::remainder).reversed()
                .thenComparingInt(AllocationRemainder::index));

        for (int index = 0; index < remainingCents; index++) {
            int itemIndex = remainders.get(index).index();
            allocations.set(itemIndex, allocations.get(itemIndex).add(ONE));
        }

        List<BigDecimal> result = new ArrayList<>(allocations.size());
        for (int index = 0; index < allocations.size(); index++) {
            BigInteger allocatedItemCents = allocations.get(index);
            if (allocatedItemCents.compareTo(itemCents.get(index)) > 0) {
                throw new IllegalArgumentException("An allocated discount cannot exceed its item subtotal.");
            }
            result.add(new BigDecimal(allocatedItemCents, 2).setScale(2, RoundingMode.UNNECESSARY));
        }
        return List.copyOf(result);
    }

    private static BigInteger toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).unscaledValue();
    }

    private record AllocationRemainder(int index, BigInteger remainder) {
    }
}
