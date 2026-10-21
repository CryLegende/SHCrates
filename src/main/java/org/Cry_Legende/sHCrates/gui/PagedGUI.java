package org.Cry_Legende.sHCrates.gui;

import org.Cry_Legende.sHCrates.crates.Crate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PagedGUI implements InventoryHolder {

    private final Inventory inventory;
    private final Crate associatedCrate; // Crate pour laquelle ce preview est affiché
    private final Player viewer;
    private final List<ItemStack> itemsToDisplay; // Tous les items à afficher sur plusieurs pages
    private final int itemsPerPage;
    private int currentPage; // 0-indexed
    private final int totalPages;

    /**
     * Constructeur pour un inventaire paginé.
     *
     * @param viewer          Le joueur qui voit ce GUI.
     * @param associatedCrate La caisse associée à ce preview.
     * @param title           Le titre de l'inventaire.
     * @param size            La taille de l'inventaire (doit être un multiple de 9).
     * @param allItems        La liste complète des ItemStacks à paginer.
     * @param itemsPerPage    Le nombre d'items à afficher par page.
     * @param initialPage     La page initiale à afficher (0-indexed).
     */
    public PagedGUI(Player viewer, Crate associatedCrate, String title, int size, List<ItemStack> allItems, int itemsPerPage, int initialPage) {
        this.viewer = viewer;
        this.associatedCrate = associatedCrate;
        this.inventory = Bukkit.createInventory(this, size, title); // 'this' en tant que holder

        this.itemsToDisplay = allItems != null ? Collections.unmodifiableList(new ArrayList<>(allItems)) : Collections.emptyList();
        this.itemsPerPage = Math.max(1, itemsPerPage); // Assurer au moins 1 item par page

        if (this.itemsToDisplay.isEmpty()) {
            this.totalPages = 1; // Toujours au moins une page, même vide
        } else {
            this.totalPages = (int) Math.ceil((double) this.itemsToDisplay.size() / this.itemsPerPage);
        }

        // S'assurer que initialPage est dans les bornes valides
        this.currentPage = Math.max(0, Math.min(initialPage, this.totalPages - 1));
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Crate getAssociatedCrate() {
        return associatedCrate;
    }

    public Player getViewer() {
        return viewer;
    }

    /**
     * @return Une liste non modifiable de tous les items destinés à être affichés dans ce GUI paginé.
     */
    public List<ItemStack> getAllItemsToDisplay() {
        return itemsToDisplay;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Définit la page actuelle, en s'assurant qu'elle reste dans les limites valides.
     *
     * @param page L'index de la page à définir (0-indexed).
     */
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, totalPages - 1));
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasNextPage() {
        return currentPage < totalPages - 1;
    }

    public boolean hasPreviousPage() {
        return currentPage > 0;
    }

    /**
     * Récupère la sous-liste d'ItemStacks pour la page actuelle.
     *
     * @return Une liste d'ItemStacks pour la page actuelle, ou une liste vide si la page est hors limites ou vide.
     */
    public List<ItemStack> getItemsForCurrentPage() {
        if (itemsToDisplay.isEmpty()) {
            return Collections.emptyList();
        }
        int startIndex = currentPage * itemsPerPage;
        // Vérifier si startIndex est hors des limites de la liste itemsToDisplay
        if (startIndex >= itemsToDisplay.size() || startIndex < 0) {
            return Collections.emptyList(); // Page vide ou invalide
        }
        int endIndex = Math.min(startIndex + itemsPerPage, itemsToDisplay.size());
        return itemsToDisplay.subList(startIndex, endIndex);
    }
}
