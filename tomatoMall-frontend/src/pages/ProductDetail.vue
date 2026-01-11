<script setup>
import ProductDetails from "@/components/ProductDetails.vue";
import NavigationBar from "@/components/NavigationBar.vue";
import {useRoute} from "vue-router";
import {computed} from 'vue';


const route = useRoute();
const productId = route.params.id;
const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')
</script>

<template>

  <NavigationBar v-if="isAdmin"></NavigationBar>
  <ProductDetails :id="productId" />

</template>

<style scoped>

</style>