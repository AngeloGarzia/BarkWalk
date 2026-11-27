import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { WalkParticipantsComponent } from './walk-participants.component';

describe('WalkParticipantsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WalkParticipantsComponent],
    }).compileComponents();
  });

  // Vérifie la création du composant participants.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // N'affiche rien si names est null (accès non autorisé).
  it("N'affiche rien si names est null.", () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    fixture.componentInstance.names = null;
    fixture.detectChanges();
    const div = fixture.nativeElement.querySelector('.participants');
    expect(div).toBeNull();
  });

  // Affiche "Aucun inscrit" si names est un tableau vide.
  it('Affiche "Aucun inscrit" si la liste est vide.', () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    fixture.componentInstance.names = [];
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucun inscrit');
  });

  // Affiche les pseudos avec @ si pas de nom composé ni préfixe @.
  it('Préfixe un pseudo simple avec @.', () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    fixture.componentInstance.names = ['alice'];
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('@alice');
  });

  // Ne double-préfixe pas un pseudo déjà à @.
  it("Ne double-préfixe pas un pseudo déjà préfixé par @.", () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    fixture.componentInstance.names = ['@bob'];
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('@bob');
    expect(text.includes('@@bob')).toBe(false);
  });

  // Les noms composés (avec espace) ne sont pas préfixés.
  it("N'ajoute pas @ pour un nom composé (prénom nom).", () => {
    const fixture = TestBed.createComponent(WalkParticipantsComponent);
    fixture.componentInstance.names = ['Jean Dupont'];
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Jean Dupont');
  });
});
