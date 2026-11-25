// Racine Angular : délègue le rendu à la coquille applicative.
import { Component } from '@angular/core';
import { ShellComponent } from './layout/shell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ShellComponent],
  template: `<app-shell />`,
})
export class App {}
