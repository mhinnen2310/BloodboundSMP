package nl.mitchsmp.core.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EconomyService {
    double getBalance(UUID playerId);

    void setBalance(UUID playerId, double amount);

    void deposit(UUID playerId, double amount, String reason);

    boolean withdraw(UUID playerId, double amount, String reason);

    boolean transfer(UUID from, UUID to, double amount, String reason);

    List<Map.Entry<UUID, Double>> topBalances(int limit);
}
