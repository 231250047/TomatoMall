import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../pages/index.vue';
import CartPage from "../pages/CartPage.vue";
import WarehousePage from "../pages/WarehousePage.vue";
import InformationPage from "../pages/InformationPage.vue";
import LoginPage from "../pages/LoginPage.vue";
import RegisterPage from "../pages/RegisterPage.vue";
import ProductDetails from "@/components/ProductDetails.vue";
import Products from "@/pages/Products.vue";
import ProductDetail from "@/pages/ProductDetail.vue";
import CreateProduct from "@/pages/CreateProduct.vue";
import UpdateProduct from "@/pages/UpdateProduct.vue";
import OrderPage from "@/pages/OrderPage.vue";
import PaymentPage from "@/pages/PaymentPage.vue";
import TagPage from "@/pages/TagPage.vue";
import RankPage from "@/pages/RankPage.vue";
import AdvertisementsPage from "@/pages/AdvertisementsPage.vue";
import ChatPage from "@/pages/ChatPage.vue";
import FavoritesPage from "@/pages/FavoritesPage.vue";
import SpecialPricePage from "@/pages/SpecialPricePage.vue";
import NewArrivalsPage from "@/pages/NewArrivalsPage.vue";
import OrderListPage from "@/pages/OrderListPage.vue";
import OrderDetailPage from "@/pages/OrderDetailPage.vue";

const routes = [
        {
            path: '/',
            redirect: '/login',
        },{
            path: '/home',
            name: 'home',
            component: HomePage,
        },
    {
        path: '/cart',
        name: 'cart',
        component: CartPage,
    },
    {
        path: '/warehouse',
        name: 'warehouse',
        component: WarehousePage,
    },
    //其他路由配置
    { // 获取和更新用户信息
        path: '/information',
        name: 'information',
        component: InformationPage,
    },{
        path: '/login',
        name: 'login',
        component: LoginPage,
    },{
        path: '/register',
        name: 'register',
        component: RegisterPage,
    },{
        path: '/products',//展示所有商品
        name: 'products',
        component: Products,
    },{
        path: '/products/:id',
        name: 'productDetail',
        component: ProductDetail,
        props:true
    },{
        // 支持短横线路径以匹配页面中使用的 '/create-product'
        path: '/create-product',
        alias: '/createProduct',
        name: 'createProduct',
        component: CreateProduct,
    },{
        path: '/update-product/:id',
        name: 'updateProduct',
        component: UpdateProduct,
    },{
        path: '/order',
        name:'order',
        component: OrderPage
    },{
        path: '/payment/:orderId',
        name: 'Payment',
        component: PaymentPage,
        meta: { requiresAuth: true }
    },{
        path: '/tag/:tag',
        name:'tag',
        component: TagPage,
        props: true              // 启用props接收参数
    },{
        path: '/rank',
        name:'rank',
        component: RankPage
    },{
        path: '/advertisements',
        name:"Advertisements",
        component: AdvertisementsPage
    },{
        path: '/chat/:sellerId',
        name: 'chat',
        component: ChatPage,
        props: true,
        meta: { requiresAuth: true }
    },{
        path: '/favorites',
        name:"Favorites",
        component: FavoritesPage
    },{
    path: '/update-product/:id',
    alias: '/updateProduct/:id',
    name: 'updateProduct',
    component: UpdateProduct,
    },{
        path: '/special-price',
        name: 'SpecialPrice',
        component: SpecialPricePage
    },{
        path: '/new-arrivals',
        name: 'NewArrivals',
        component: NewArrivalsPage
    },{
        path: '/orders',
        name: 'Orders',
        component: OrderListPage
    },{
        path: '/orders/:orderId',
        name: 'OrderDetail',
        component: OrderDetailPage
    }

];

const router = createRouter({
    history: createWebHistory(),
    routes
});


export default router;
