package org.bukkit.inventory.meta;

import java.util.List;

public interface BookMeta extends ItemMeta {
    void setTitle(String title);

    String getTitle();

    void setAuthor(String author);

    String getAuthor();

    void setPages(List<String> pages);

    void addPage(String... pages);
}
