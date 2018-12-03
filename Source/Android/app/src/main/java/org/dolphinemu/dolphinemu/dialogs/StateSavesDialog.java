package org.dolphinemu.dolphinemu.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nononsenseapps.filepicker.DividerItemDecoration;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;

import java.io.File;
import java.util.ArrayList;

public class StateSavesDialog extends DialogFragment
{
  public class StateSaveModel
  {
    private String mFilename;
    private long mLastModified;

    public StateSaveModel(String filename, long lastModified)
    {
      mFilename = filename;
      mLastModified = lastModified;
    }
  }

  public class StateSaveViewHolder extends RecyclerView.ViewHolder
    implements View.OnClickListener
  {
    public StateSaveViewHolder(View itemView)
    {
      super(itemView);
      itemView.setOnClickListener(this);
    }

    public void bind(StateSaveModel item)
    {

    }

    public void onClick(View clicked)
    {

    }
  }

  public class StateSavesAdapter extends RecyclerView.Adapter<StateSaveViewHolder>
  {
    private static final int NUM_STATES = 10;
    private ArrayList<StateSaveModel> mStateSaves;

    public StateSavesAdapter(String gameId)
    {
      final String statePath = DirectoryInitialization.getDolphinDirectory() + "/StateSaves/";
      mStateSaves = new ArrayList<>();
      for (int i = 1; i < NUM_STATES; ++i)
      {
        String filename = String.format("%s%s.s%02d", statePath, gameId, i);
        File stateFile = new File(filename);
        if (stateFile.exists())
        {
          mStateSaves.add(new StateSaveModel(filename, stateFile.lastModified()));
        }
      }
    }

    @NonNull
    @Override
    public StateSaveViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      View itemView = inflater.inflate(R.layout.list_item_running_checkbox, parent, false);
      return new StateSaveViewHolder(itemView);
    }

    @Override
    public int getItemCount()
    {
      return mStateSaves.size();
    }

    @Override
    public int getItemViewType(int position)
    {
      return 0;
    }

    @Override
    public void onBindViewHolder(@NonNull StateSaveViewHolder holder, int position)
    {
      holder.bind(mStateSaves.get(position));
    }
  }

  public static StateSavesDialog newInstance(String gameId)
  {
    StateSavesDialog fragment = new StateSavesDialog();
    Bundle arguments = new Bundle();
    arguments.putString(ARG_GAME_ID, gameId);
    fragment.setArguments(arguments);
    return fragment;
  }

  private static final String ARG_GAME_ID = "game_id";
  private StateSavesAdapter mAdapter;

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    ViewGroup contents = (ViewGroup) getActivity().getLayoutInflater()
      .inflate(R.layout.dialog_running_settings, null);

    TextView textTitle = contents.findViewById(R.id.text_title);
    textTitle.setText(R.string.state_saves);

    int columns = 1;
    Drawable lineDivider = getContext().getDrawable(R.drawable.line_divider);
    RecyclerView recyclerView = contents.findViewById(R.id.list_settings);
    RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), columns);
    recyclerView.setLayoutManager(layoutManager);
    mAdapter = new StateSavesAdapter(getArguments().getString(ARG_GAME_ID));
    recyclerView.setAdapter(mAdapter);
    recyclerView.addItemDecoration(new DividerItemDecoration(lineDivider));
    builder.setView(contents);
    return builder.create();
  }
}
