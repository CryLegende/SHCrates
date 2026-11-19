package org.Cry_Legende.sHCrates.crates;

import org.Cry_Legende.sHCrates.scenes.ParticleSpec;

import java.util.List;

/**
 * Essayage d'un cosmétique par le reflet du Miroir (clé {@code Essayage} d'une récompense).
 *
 * @param type      ce que le reflet essaie
 * @param title     TITRE : texte affiché devant le pseudo du reflet (couleurs &)
 * @param particles PARTICULE : particules montrées autour du reflet
 */
public record TryOn(Type type, String title, List<ParticleSpec> particles) {

    public enum Type {
        /** L'objet du lot se pose sur l'épaule du reflet, comme un compagnon. */
        COMPAGNON,
        /** Les particules entourent le reflet. */
        PARTICULE,
        /** Le titre s'affiche devant le pseudo du reflet. */
        TITRE
    }
}
