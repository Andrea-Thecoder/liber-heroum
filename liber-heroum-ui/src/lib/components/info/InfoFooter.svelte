<script lang="ts">
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import BrandLink from '#lib/components/ui/BrandLink.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import FooterLink from '#lib/components/ui/FooterLink.svelte';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';
	import FooterColumn from './FooterColumn.svelte';

	const disabled = $derived(auth.status === 'loading' || auth.status === 'error');
</script>

<Container
	as="footer"
	class="border-t border-(color:--rule) pt-10 pb-[max(40px,env(safe-area-inset-bottom))] text-soft"
>
	<div class="flex flex-wrap justify-between gap-x-12 gap-y-8">
		<div class="max-w-[22rem]">
			<BrandLink />
			<p class="mx-0 mt-2 mb-0 text-[17px]">
				Un diario per ogni eroe, un posto per le sue cronache.
			</p>
		</div>
		<div class="flex flex-wrap gap-x-14 gap-y-8">
			<FooterColumn title="Prodotto">
				<FooterLink href={resolve('/piani')}>Piani</FooterLink>
				<FooterLink href={resolve('/faq')}>Domande frequenti</FooterLink>
			</FooterColumn>
			<FooterColumn title="Aiuto">
				<FooterLink href={resolve('/feedback')}>Feedback</FooterLink>
				<FooterLink href={resolve('/contatti')}>Contatti</FooterLink>
			</FooterColumn>
			<FooterColumn title="Legale">
				<FooterLink href={resolve('/privacy')}>Privacy</FooterLink>
				{#if auth.status === 'authenticated'}
					<FooterLink href={resolve('/(app)/profile')}>Il mio profilo</FooterLink>
				{:else}
					<LinkButton onclick={loginToApp} {disabled}>Accedi</LinkButton>
				{/if}
			</FooterColumn>
		</div>
	</div>
	<p class="mx-0 mt-8 mb-0 text-[16px]">
		{new Date().getFullYear()} Liber Heroum. Fatto per chi gioca di ruolo.
	</p>
</Container>
