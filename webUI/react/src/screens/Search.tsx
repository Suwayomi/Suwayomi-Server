import React, { useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import MangaGrid from '../components/MangaGrid';

const useStyles = makeStyles((theme) => ({
    root: {
        TextField: {
            margin: theme.spacing(1),
            width: '25ch',
        },
    },
}));

export default function Search() {
    const classes = useStyles();
    const [error, setError] = useState<boolean>(false);
    const [mangas, setMangas] = useState<IManga[]>([]);
    const [message, setMessage] = useState<string>('');

    const textInput = React.createRef<HTMLInputElement>();

    function doSearch() {
        if (textInput.current) {
            const { value } = textInput.current;
            if (value === '') { setError(true); } else {
                setError(false);
                setMangas([]);
                setMessage('button pressed');
            }
        }
    }

    const mangaGrid = <MangaGrid mangas={mangas} message={message} />;

    return (
        <>
            <form className={classes.root} noValidate autoComplete="off">
                <TextField inputRef={textInput} error={error} id="standard-basic" label="Search text.." />
                <Button variant="contained" color="primary" onClick={() => doSearch()}>
                    Primary
                </Button>
            </form>
            {mangaGrid}
        </>
    );
}
