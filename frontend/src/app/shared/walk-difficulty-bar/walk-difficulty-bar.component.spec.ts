import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { WalkDifficultyBarComponent } from './walk-difficulty-bar.component';

describe('WalkDifficultyBarComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WalkDifficultyBarComponent],
    }).compileComponents();
  });

  // Vérifie la création du composant indicateur de difficulté.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(WalkDifficultyBarComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // N'affiche rien si le niveau est null.
  it("N'affiche rien si level est null.", () => {
    const fixture = TestBed.createComponent(WalkDifficultyBarComponent);
    fixture.componentRef.setInput('level', null);
    fixture.detectChanges();
    const meter = fixture.nativeElement.querySelector('.diff-meter');
    expect(meter).toBeNull();
  });

  // Affiche le compteur pour un niveau valide.
  it('Affiche le compteur pour level = 5.', () => {
    const fixture = TestBed.createComponent(WalkDifficultyBarComponent);
    fixture.componentRef.setInput('level', 5);
    fixture.detectChanges();
    const caption = fixture.nativeElement.querySelector('.diff-caption');
    expect(caption).not.toBeNull();
    expect(caption.textContent).toContain('5/10');
  });

  // La bande "easy" est appliquée pour les niveaux <= 4.
  it('Applique la bande "easy" pour level = 3.', () => {
    const fixture = TestBed.createComponent(WalkDifficultyBarComponent);
    fixture.componentRef.setInput('level', 3);
    fixture.detectChanges();
    const meter = fixture.nativeElement.querySelector('.diff-meter');
    expect(meter?.getAttribute('data-band')).toBe('easy');
  });

  // La bande "hard" est appliquée pour les niveaux >= 8.
  it('Applique la bande "hard" pour level = 9.', () => {
    const fixture = TestBed.createComponent(WalkDifficultyBarComponent);
    fixture.componentRef.setInput('level', 9);
    fixture.detectChanges();
    const meter = fixture.nativeElement.querySelector('.diff-meter');
    expect(meter?.getAttribute('data-band')).toBe('hard');
  });
});
