import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { CharacterDetail } from '#lib/characters/api.js';
import OtherScreen from './OtherScreen.svelte';

const character: CharacterDetail = {
	id: 'id-alira',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	avatarUrl: null
};

describe('OtherScreen', () => {
	it('opens on the Altro tab of the character', async () => {
		render(OtherScreen, { character });

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Alira dei Salici');
		await expect
			.element(page.getByRole('link', { name: 'Altro' }))
			.toHaveAttribute('aria-current', 'page');
	});

	it.each(['Mappe', 'Immagini', 'Appunti e documenti'])(
		'previews the %s that are coming',
		async (title) => {
			render(OtherScreen, { character });

			await expect.element(page.getByRole('heading', { name: title })).toBeVisible();
		}
	);

	it('shows a picture for each preview', () => {
		render(OtherScreen, { character });

		expect(page.getByRole('img', { name: /in arrivo/ }).elements()).toHaveLength(3);
	});

	it('has nothing to click on the previews', () => {
		render(OtherScreen, { character });

		const buttons = page.getByRole('button', { name: 'In arrivo' });

		expect(buttons.elements().map((button) => (button as HTMLButtonElement).disabled)).toEqual([
			true,
			true,
			true
		]);
	});
});
