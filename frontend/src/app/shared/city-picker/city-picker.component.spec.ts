import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { CityPickerComponent } from './city-picker.component';

describe('CityPickerComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CityPickerComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((r) => r.flush([]));
  });

  // Vérifie la création du sélecteur de ville.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(CityPickerComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // La liste de résultats est vide à l'initialisation.
  it("Aucun résultat à l'initialisation.", () => {
    const fixture = TestBed.createComponent(CityPickerComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance as any;
    expect(comp.results()).toEqual([]);
  });

  // initialCity pré-remplit l'input.
  it("initialCity pré-remplit le champ de recherche.", () => {
    const fixture = TestBed.createComponent(CityPickerComponent);
    fixture.componentInstance.initialCity = { id: 1, name: 'Paris', postalCode: '75001' };
    fixture.detectChanges();
    expect(fixture.componentInstance.searchCtrl.value).toBe('Paris');
  });
});
