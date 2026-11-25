// Champ de recherche ville / code postal avec suggestions.
import { Component, Output, EventEmitter, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { CityService, City } from '../city.service';

@Component({
  selector: 'app-city-picker',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="city-picker">
      <input
        [formControl]="searchCtrl"
        type="text"
        placeholder="Chercher une ville…"
        autocomplete="off"
        class="city-input"
        aria-label="Rechercher une ville"
      />
      @if (results().length > 0) {
        <ul class="city-dropdown" role="listbox">
          @for (city of results(); track city.id) {
            <li
              role="option"
              tabindex="0"
              (click)="select(city)"
              (keydown.enter)="select(city)"
            >
              {{ city.name }}
              <span class="postal">{{ city.postalCode }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [
    `
      .city-picker {
        position: relative;
      }
      .city-input {
        width: 100%;
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--line);
        border-radius: 8px;
        font-size: 0.95rem;
        font-family: var(--font-body);
        background: white;
        outline: none;
        transition: border-color 0.15s;
      }
      .city-input:focus {
        border-color: var(--accent);
      }
      .city-dropdown {
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        right: 0;
        background: white;
        border: 1px solid var(--line);
        border-radius: 8px;
        list-style: none;
        margin: 0;
        padding: 4px 0;
        z-index: 100;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
      }
      .city-dropdown li {
        padding: 0.5rem 0.75rem;
        cursor: pointer;
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.95rem;
      }
      .city-dropdown li:hover,
      .city-dropdown li:focus {
        background: var(--bg);
        outline: none;
      }
      .postal {
        color: var(--muted);
        font-size: 0.82em;
      }
    `,
  ],
})
export class CityPickerComponent implements OnInit, OnDestroy {
  @Output() citySelected = new EventEmitter<City>();

  private readonly cityService = inject(CityService);
  private sub?: Subscription;

  protected readonly results = signal<City[]>([]);
  readonly searchCtrl = new FormControl('');

  ngOnInit(): void {
    this.sub = this.searchCtrl.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(q => (q && q.length >= 2 ? this.cityService.search(q) : of([]))),
        catchError(() => of([])),
      )
      .subscribe(cities => this.results.set(cities));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected select(city: City): void {
    this.results.set([]);
    this.searchCtrl.setValue(city.name, { emitEvent: false });
    this.citySelected.emit(city);
  }
}
