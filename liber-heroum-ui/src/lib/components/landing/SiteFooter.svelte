<script lang="ts">
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import { registerToApp } from '#lib/auth/register.js';
	import Container from '#lib/components/ui/Container.svelte';
	import FooterLink from '#lib/components/ui/FooterLink.svelte';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';

	const disabled = $derived(auth.status === 'loading' || auth.status === 'error');
</script>

<Container
	as="footer"
	class="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-(color:--rule) pt-7 pb-[max(40px,env(safe-area-inset-bottom))] text-base text-soft"
>
	<div class="font-display text-[26px] font-bold text-ink">Liber Heroum</div>
	<div
		class="grid grid-cols-3 gap-x-5 gap-y-1 min-[561px]:flex min-[561px]:flex-wrap min-[561px]:gap-x-6"
	>
		<FooterLink href={resolve('/piani')}>Piani</FooterLink>
		<FooterLink href={resolve('/faq')}>FAQ</FooterLink>
		<FooterLink href={resolve('/contatti')}>Contatti</FooterLink>
		<FooterLink href={resolve('/privacy')}>Privacy</FooterLink>
		{#if auth.status !== 'authenticated'}
			<LinkButton onclick={loginToApp} {disabled}>Accedi</LinkButton>
			<LinkButton onclick={registerToApp} {disabled}>Registrati</LinkButton>
		{/if}
	</div>
</Container>
