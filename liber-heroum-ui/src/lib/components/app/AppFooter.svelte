<script lang="ts">
	import { resolve } from '$app/paths';
	import Container from '#lib/components/ui/Container.svelte';
	import FooterLink from '#lib/components/ui/FooterLink.svelte';
</script>

<Container
	as="footer"
	class="mt-[clamp(40px,6vw,72px)] flex flex-wrap items-center justify-between gap-x-6 gap-y-1 border-t border-(color:--rule) pt-3 pb-[max(24px,env(safe-area-inset-bottom))] text-[17px]"
>
	<span class="font-display text-[22px] font-bold text-ink">Liber Heroum</span>
	<nav
		aria-label="Pagine informative"
		class="grid grid-cols-3 gap-x-5 min-[561px]:flex min-[561px]:flex-wrap min-[561px]:gap-x-6"
	>
		<FooterLink href={resolve('/piani')}>Piani</FooterLink>
		<FooterLink href={resolve('/faq')}>FAQ</FooterLink>
		<FooterLink href={resolve('/feedback')}>Feedback</FooterLink>
		<FooterLink href={resolve('/contatti')}>Contatti</FooterLink>
		<FooterLink href={resolve('/privacy')}>Privacy</FooterLink>
	</nav>
</Container>
