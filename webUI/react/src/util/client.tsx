import axios from 'axios';
import storage from './storage';

const clientMaker = () => axios.create({
    baseURL: storage.getItem('baseURL', 'http://127.0.0.1:4567'),
});

const client = clientMaker();

export default client;
