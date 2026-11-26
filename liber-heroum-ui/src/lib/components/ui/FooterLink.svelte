<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAnchorAttributes } from 'svelte/elements';

	interface Props extends HTMLAnchorAttributes {
		href: string;
		children: Snippet;
		class?: string;
	}

	const { href, children, class: className = '', ...rest }: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -- gli href arrivano già risolti con resolve() da chi usa il componente -->
<a
	{href}
	class="inline-flex min-h-[44px] min-w-[44px] items-center text-soft underline underline-offset-4 hover:opacity-80 {className}"
	{...rest}>{@render children()}</a
>
<!-- eslint-enable svelte/no-navigation-without-resolve -->
